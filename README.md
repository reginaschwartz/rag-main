# RAG API with PGVector

Minimal retrieval-augmented generation (RAG) service built with Spring Boot, OpenAI embeddings/chat,
and PostgreSQL + pgvector.

Get an API key for OpenAI: https://platform.openai.com/api-keys

## What this project does

- Indexes text or PDF content into a PGVector collection.
- Stores chunk metadata (including optional `context_tag`) for filtered retrieval.
- Answers questions by retrieving relevant chunks and sending them to an LLM.
- Supports both API-based ingestion (`/index`) and command-line ingestion (`--rag.cli=index`).

The service reads and writes the same `langchain_pg_collection` / `langchain_pg_embedding` tables the
earlier Python implementation used, so existing collections keep working.

## Project structure

The application lives in `rag-java`:

- `rag-java/pom.xml` Maven build (Spring Boot 3.4, Java 21).
- `web/RagController.java` `/index` and `/query` endpoints.
- `web/dto/` request and response models.
- `web/ApiExceptionHandler.java` renders errors as `{"detail": "..."}`.
- `vectorstore/PgVectorStore.java` collection setup, chunk storage, and similarity search.
- `vectorstore/PgVectorConnection.java` connection resolution from environment variables.
- `document/` text splitting, directory loading, and PDF/text extraction.
- `openai/OpenAiClient.java` embeddings and chat completion calls.
- `service/` ingestion and query flows plus the prompt template.
- `cli/` one-shot bulk indexing and query commands.
- `docker-compose.yml` app + pgvector Postgres services.
- `init.sql` creates the `vector` extension in Postgres.

The original Python files (`api.py`, `create_database.py`, `query_data.py`, `models.py`,
`vector_store.py`, `requirements.txt`, `Dockerfile`) are kept for reference and are no longer used.

## Prerequisites

- Java 21 and Maven 3.9+ (for the local workflow)
- Docker and Docker Compose (for the container workflow)
- OpenAI API key

## Environment variables

Create a `.env` file in the repo root:

```bash
OPENAI_API_KEY=your_openai_key
PGVECTOR_COLLECTION=default

# Optional if not using PGVECTOR_CONNECTION directly
POSTGRES_HOST=localhost
POSTGRES_PORT=5432
POSTGRES_DB=ragdb
POSTGRES_USER=raguser
POSTGRES_PASSWORD=ragpass
```

Notes:

- In Docker Compose, `PGVECTOR_CONNECTION` is already set for the app service.
- If `PGVECTOR_CONNECTION` is present, it is used first. Both `postgresql://` and the SQLAlchemy style
  `postgresql+psycopg://` schemes are accepted.
- `.env` is read as a properties file when running locally, so values must not be quoted.
- Optional overrides: `OPENAI_CHAT_MODEL` (default `gpt-4o-mini`), `OPENAI_EMBEDDING_MODEL`
  (default `text-embedding-ada-002`), `OPENAI_TEMPERATURE`, `RAG_DATA_PATH` (default `data/books`).

The embedding model determines the vector dimension, so keep it unchanged for an existing collection.

## Run with Docker (recommended)

1. Start services:

```bash
docker compose up --build
```

2. API will be available at:

```text
http://localhost:8000
```

3. Open interactive docs:

```text
http://localhost:8000/docs
```

Details:

- Postgres runs from `pgvector/pgvector:pg16`.
- `init.sql` is mounted into `/docker-entrypoint-initdb.d/` so `CREATE EXTENSION vector` runs on first
  database initialization.
- The app creates its tables, indexes and collection during startup, before it accepts requests.
- `./data` is mounted into the container so the bulk indexer can read documents from the host.

## Run locally (without Docker)

1. Ensure a Postgres database with the pgvector extension is available.

2. Start the API:

```bash
cd rag-java
mvn spring-boot:run
```

Or build and run the jar:

```bash
cd rag-java
mvn package
java -jar target/rag-api-1.0.0.jar
```

## Ingestion and query flows

### API ingestion (`/index`)

- Method: `POST`
- Content type: `multipart/form-data`
- Required file field: `file`
- Optional query params:
  - `metadata_json` JSON object as a string
  - `reset_collection` boolean (default `false`)
  - `context_tag` string

Example:

```bash
curl -X POST "http://localhost:8000/index?reset_collection=true&context_tag=book&metadata_json=%7B%22source%22%3A%22alice_in_wonderland.md%22%7D" \
  -F "file=@data/alice_in_wonderland.md" \
  -H "accept: application/json"
```

### API query (`/query`)

- Method: `POST`
- Content type: `application/json`
- Body:
  - `query_text` string
  - `k` int (default `3`)
  - `min_relevance` float (default `0.7`)
  - `context_tag` optional string for metadata filtering

Example:

```bash
curl -X POST "http://localhost:8000/query" \
  -H "Content-Type: application/json" \
  -d '{
    "query_text": "Who is Alice?",
    "k": 3,
    "min_relevance": 0.7,
    "context_tag": "book"
  }'
```

## Command-line workflows

Both commands run the application without starting the HTTP server.

### Bulk index markdown files

Indexes `data/books/*.md` and resets the collection:

```bash
cd rag-java
mvn -q spring-boot:run -Dspring-boot.run.arguments=--rag.cli=index
```

With the packaged jar, or inside Docker Compose:

```bash
java -jar target/rag-api-1.0.0.jar --rag.cli=index
docker compose run --rm app --rag.cli=index
```

Ensure your markdown files are under `data/books`, or point `RAG_DATA_PATH` somewhere else.

### Query from the command line

```bash
java -jar target/rag-api-1.0.0.jar --rag.cli=query "What happens to Alice?"
docker compose run --rm app --rag.cli=query "What happens to Alice?"
```

Add `--rag.context-tag=book` to filter by metadata.

## Testing

```bash
cd rag-java
mvn test
```

Tests cover the text splitter, file-content extraction, prompt formatting, connection resolution, and
the HTTP endpoints.

`PgVectorStoreIntegrationTest` additionally exercises the real pgvector SQL and only runs when a
database is provided:

```bash
docker compose up -d postgres
RAG_IT_CONNECTION=postgresql://raguser:ragpass@localhost:5432/ragdb mvn test
```

## Notes on behaviour

- Markdown files are indexed as plain text; the Python version ran them through `unstructured` first,
  so chunk boundaries can differ slightly for the same input.
- Relevance is reported as `1 - cosine_distance`, the same score the Python version returned.
- An invalid `metadata_json` value returns `400` with a `detail` message.

## Troubleshooting

- `Unable to find matching results`: lower `min_relevance` or index more data.
- `Uploaded file must be UTF-8 text or PDF`: upload UTF-8 text or a PDF with extractable text.
- Connection errors: verify Postgres is reachable and env vars match actual credentials.
- `OPENAI_API_KEY is not set`: add the key to `.env` or the environment.
