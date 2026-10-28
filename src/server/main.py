from fastapi import FastAPI

app = FastAPI(title="AI Server")


@app.get("/health")
def health() -> dict[str, str]:
    return {"status": "ok"}
