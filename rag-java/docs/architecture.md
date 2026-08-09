# Architecture

The service answers questions from documents it has indexed. Text is split into chunks, each chunk is
embedded with OpenAI and stored in PostgreSQL with the pgvector extension; a query embeds the question,
retrieves the nearest chunks and asks a chat model to answer from them.

The database layout is the one LangChain's `PGVector` uses (`langchain_pg_collection` and
`langchain_pg_embedding`), so collections written by the earlier Python implementation remain readable.

## Components

```mermaid
flowchart TD
    client["HTTP client"]
    cliUser["java -jar rag-api.jar --rag.cli=index or query"]

    subgraph web["web"]
        controller["RagController<br/>POST /index, POST /query"]
        advice["ApiExceptionHandler<br/>errors as detail JSON"]
    end

    subgraph cliPkg["cli"]
        indexRunner["CreateDatabaseRunner"]
        queryRunner["QueryDataRunner"]
    end

    subgraph svc["service"]
        indexing["IndexingService"]
        querySvc["QueryService"]
        prompt["PromptTemplate"]
    end

    subgraph doc["document"]
        loader["DirectoryDocumentLoader<br/>data/books/*.md"]
        extractor["ContentExtractor<br/>PDFBox or strict UTF-8"]
        splitter["RecursiveCharacterTextSplitter<br/>300 chars, 100 overlap"]
    end

    subgraph vs["vectorstore"]
        store["PgVectorStore"]
        init["VectorStoreInitializer<br/>DDL at startup"]
    end

    openai["OpenAiClient<br/>embeddings + chat"]
    config["config<br/>AppConfig, properties,<br/>PgVectorConnection"]

    db[("PostgreSQL + pgvector<br/>langchain_pg_collection<br/>langchain_pg_embedding")]
    api["OpenAI API"]

    client --> controller
    cliUser --> indexRunner
    cliUser --> queryRunner
    controller --> indexing
    controller --> querySvc
    controller -.-> advice
    indexRunner --> indexing
    queryRunner --> querySvc
    indexing --> extractor
    indexing --> loader
    indexing --> splitter
    indexing --> store
    querySvc --> store
    querySvc --> prompt
    querySvc --> openai
    store --> openai
    store --> db
    init --> store
    openai --> api
    config --> store
    config --> openai
    config --> db
```

There are two entry points into the same services: `RagController` over HTTP, and the `cli` runners for
one-shot commands. `RagApplication` disables the web server when it sees a `--rag.cli=` argument, so the
ingestion and retrieval logic exists in one place regardless of how it is invoked.

`PgVectorStore` is the only class that talks to the database and `OpenAiClient` the only one that leaves
the process for the model. That boundary is what allows the store to be tested against a real Postgres
with a stubbed embedder.

## Ingestion

```mermaid
sequenceDiagram
    participant C as Client or CLI
    participant I as IndexingService
    participant E as Extractor or Loader
    participant T as TextSplitter
    participant S as PgVectorStore
    participant O as OpenAI
    participant D as Postgres

    C->>I: index upload or bulk directory
    I->>E: read text
    E-->>I: documents with source metadata
    I->>T: split
    T-->>I: chunks carrying start_index
    I->>I: stamp context_tag when provided
    I->>S: addDocuments(chunks, preDelete)
    S->>O: POST /embeddings, batched
    O-->>S: one vector per chunk
    S->>D: optional collection delete, then batch insert
    S-->>I: stored
    I-->>C: documents, chunks, collection
```

An upload produces a single document through `ContentExtractor`, which reads PDFs with PDFBox and
otherwise decodes strict UTF-8. The bulk command produces one document per matching file through
`DirectoryDocumentLoader`. Both then share the same splitter and storage path.

Embeddings are computed before the transaction opens, so no database connection is held during the
OpenAI round trip. `reset_collection` (or the bulk command, which always resets) deletes the collection
row first and relies on `ON DELETE CASCADE` to remove its chunks.

## Query

```mermaid
sequenceDiagram
    participant C as Client
    participant R as RagController
    participant Q as QueryService
    participant S as PgVectorStore
    participant O as OpenAI
    participant D as Postgres

    C->>R: POST /query with query_text, k, min_relevance, context_tag
    R->>Q: answer(...)
    Q->>S: similaritySearchWithRelevanceScores
    S->>O: POST /embeddings for the question
    O-->>S: query vector
    S->>D: SELECT ordered by cosine distance, LIMIT k
    D-->>S: document, cmetadata, distance
    S-->>Q: chunks scored as 1 minus distance
    Q->>Q: 404 if empty or best score below min_relevance
    Q->>O: POST /chat/completions with context and question
    O-->>Q: answer text
    Q-->>R: answer plus sources
    R-->>C: 200 with response and sources
```

Relevance is reported as `1 - cosine_distance`, matching what LangChain's
`similarity_search_with_relevance_scores` returned, so `min_relevance` thresholds carry over unchanged.
A `context_tag` becomes an equality filter on `cmetadata->>'context_tag'`; the key is inlined into the
SQL (after validation) so the expression index can be used, while the value stays a bound parameter.

## Storage

`VectorStoreInitializer` runs during startup, before the HTTP server accepts traffic, and creates:

```sql
CREATE TABLE langchain_pg_collection (
    uuid      UUID PRIMARY KEY,
    name      VARCHAR NOT NULL UNIQUE,
    cmetadata JSON
);

CREATE TABLE langchain_pg_embedding (
    id            VARCHAR PRIMARY KEY,
    collection_id UUID REFERENCES langchain_pg_collection(uuid) ON DELETE CASCADE,
    embedding     VECTOR,
    document      VARCHAR,
    cmetadata     JSONB
);
```

Plus `CREATE EXTENSION vector`, a GIN index on `cmetadata`, and an expression index on
`cmetadata->>'context_tag'`. Every statement is idempotent, so startup is safe against an existing
database. The `embedding` column is declared without a fixed dimension, which is why changing the
embedding model on an existing collection produces mismatched vectors rather than an error — keep
`OPENAI_EMBEDDING_MODEL` stable once data is indexed.

## Configuration

Settings come from environment variables, resolved in `application.yml` and bound to records in the
`config` package. A `.env` file is imported when present.

- `OPENAI_API_KEY`, and optionally `OPENAI_CHAT_MODEL`, `OPENAI_EMBEDDING_MODEL`, `OPENAI_TEMPERATURE`
- `PGVECTOR_CONNECTION` (accepts `postgresql://` and SQLAlchemy's `postgresql+psycopg://`), or the
  individual `POSTGRES_HOST`, `POSTGRES_PORT`, `POSTGRES_DB`, `POSTGRES_USER`, `POSTGRES_PASSWORD`
- `PGVECTOR_COLLECTION` selects the collection, `RAG_DATA_PATH` the directory for bulk indexing

`PgVectorConnection` turns whichever form is provided into a JDBC URL plus credentials, and `AppConfig`
builds the `DataSource` and the OpenAI `RestClient` from it.

## Errors

`ApiException` carries an HTTP status and a message; `ApiExceptionHandler` renders every failure as
`{"detail": "..."}`, the shape FastAPI produced. Missing or unreadable request input returns `422`,
bad `metadata_json` or an unusable upload returns `400`, no sufficiently relevant chunk returns `404`,
and anything unexpected is logged and returned as `500`.
