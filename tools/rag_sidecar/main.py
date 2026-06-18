import logging
import re
from fastapi import FastAPI, Query, HTTPException
from pydantic import BaseModel
import httpx
from bs4 import BeautifulSoup
from contextlib import asynccontextmanager

logging.basicConfig(level=logging.INFO, format="%(asctime)s - %(levelname)s - %(message)s")
logger = logging.getLogger(__name__)

model = None

@asynccontextmanager
async def lifespan(app: FastAPI):
    global model
    from fastembed import TextEmbedding
    model = TextEmbedding(model_name="sentence-transformers/paraphrase-multilingual-MiniLM-L12-v2", show_progress_bar=False)
    logger.info("fastembed model loaded (paraphrase-multilingual-MiniLM-L12-v2, 384 dims)")
    yield
    model = None

app = FastAPI(title="RAG Sidecar", version="2.2.0", lifespan=lifespan)

BASE_URL = "https://kpopping.com"
HEADERS = {
    "User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36",
    "Accept": "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8",
    "Accept-Language": "en-US,en;q=0.9",
}
TIMEOUT = 30
client = httpx.AsyncClient(timeout=TIMEOUT, headers=HEADERS, follow_redirects=True)


def extract_content(soup: BeautifulSoup) -> str:
    # Remove junk
    for tag in soup.find_all(["script", "style", "nav", "header", "footer", "aside", "noscript", ".sidebar", ".ad-container", ".adsbygoogle"]):
        tag.decompose()

    # Special handling for tables (Awards, Music Show Wins)
    for table in soup.find_all("table"):
        caption = table.find("caption")
        caption_text = caption.get_text(strip=True) if caption else "Table"
        table_data = []
        for row in table.find_all("tr"):
            cols = [ele.get_text(separator=" ", strip=True) for ele in row.find_all(["td", "th"])]
            if cols:
                table_data.append(" | ".join(cols))
        if table_data:
            table_md = f"\n### {caption_text}\n" + "\n".join(table_data) + "\n"
            table.replace_with(table_md)

    selectors = [".profile-info", ".wiki-content", "article", ".container main", "main", "body"]
    for sel in selectors:
        elem = soup.select_one(sel)
        if elem:
            text = elem.get_text(separator="\n", strip=True)
            # Cleanup excessive newlines
            text = re.sub(r"\n{3,}", "\n\n", text)
            if len(text) > 200:
                return text
    
    body = soup.find("body")
    if body:
        return re.sub(r"\n{3,}", "\n\n", body.get_text(separator="\n", strip=True))
    return ""


@app.get("/health")
async def health():
    return {"status": "ok", "model": "sentence-transformers/paraphrase-multilingual-MiniLM-L12-v2"}


@app.get("/fetch")
async def fetch(page: str = Query(..., min_length=1)):
    url = f"{BASE_URL}{page}" if page.startswith("/") else f"{BASE_URL}/{page}"
    try:
        logger.info("Fetching page=%s", page)
        resp = await client.get(url)
        resp.raise_for_status()
        soup = BeautifulSoup(resp.text, "lxml")
        title_tag = soup.title
        title = title_tag.string.strip() if title_tag else page
        content = extract_content(soup)
        if not content:
            raise HTTPException(status_code=404, detail=f"No content found for page '{page}'")
        logger.info("Successfully fetched page=%s (%d chars)", page, len(content))
        return {"title": title, "content": content}
    except httpx.TimeoutException:
        raise HTTPException(status_code=504, detail=f"Timeout fetching page '{page}'")
    except httpx.HTTPStatusError as e:
        if e.response.status_code == 404:
            raise HTTPException(status_code=404, detail=f"Page '{page}' not found on kpopping")
        raise HTTPException(status_code=502, detail=f"HTTP {e.response.status_code} fetching page '{page}'")
    except httpx.RequestError as e:
        raise HTTPException(status_code=502, detail=f"Error fetching page '{page}': {e}")


class EmbedRequest(BaseModel):
    text: str

@app.post("/embed")
async def embed_text(req: EmbedRequest):
    if model is None:
        raise HTTPException(status_code=503, detail="Embedding model not loaded")
    if not req.text.strip():
        raise HTTPException(status_code=400, detail="Text cannot be empty")
    try:
        vec = list(model.embed(req.text))[0]
        return {"embedding": [float(v) for v in vec]}
    except Exception as e:
        logger.error("Embedding error: %s", e)
        raise HTTPException(status_code=500, detail=f"Embedding failed: {e}")


@app.get("/fetch-batch")
async def fetch_batch(pages: str = Query(..., min_length=1)):
    page_list = [p.strip() for p in pages.split(",") if p.strip()]
    if not page_list:
        raise HTTPException(status_code=400, detail="No valid pages provided")
    from concurrent.futures import ThreadPoolExecutor, as_completed
    results = {}
    errors = {}
    with ThreadPoolExecutor(max_workers=5) as executor:
        fut_map = {executor.submit(_fetch_sync, p): p for p in page_list}
        for fut in as_completed(fut_map):
            p = fut_map[fut]
            try:
                results[p] = fut.result()
            except HTTPException as e:
                errors[p] = e.detail
            except Exception as e:
                errors[p] = str(e)
    return {"results": results, "errors": errors}


def _fetch_sync(page: str):
    import requests as sync_req
    url = f"{BASE_URL}{page}" if page.startswith("/") else f"{BASE_URL}/{page}"
    resp = sync_req.get(url, headers=HEADERS, timeout=TIMEOUT)
    resp.raise_for_status()
    soup = BeautifulSoup(resp.text, "lxml")
    title_tag = soup.title
    title = title_tag.string.strip() if title_tag else page
    content = extract_content(soup)
    return {"title": title, "content": content}
