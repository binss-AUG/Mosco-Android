import httpx
from bs4 import BeautifulSoup
import asyncio

async def test_page(url):
    async with httpx.AsyncClient(headers={"User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36"}) as client:
        try:
            resp = await client.get(url, follow_redirects=True)
            soup = BeautifulSoup(resp.text, 'lxml')
            title = soup.title.string if soup.title else 'No Title'
            print(f"URL: {url}")
            print(f"Title: {title}")
            
            # Check content extraction
            for tag in soup.find_all(["script", "style", "nav", "header", "footer", "aside", "noscript", ".sidebar", ".ad-container"]):
                tag.decompose()
            selectors = [".profile-info", ".wiki-content", "article", ".container main", "main", "body"]
            content = ""
            for sel in selectors:
                elem = soup.select_one(sel)
                if elem:
                    text = elem.get_text(separator="\n", strip=True)
                    if len(text) > 200:
                        content = text
                        print(f"Content found via selector: {sel}")
                        break
            print(f"Content length: {len(content)}")
            print("-" * 40)
        except Exception as e:
            print(f"Error fetching {url}: {e}")

async def main():
    urls = [
        "https://kpopping.com/musicalbum/2026-baby-flower-city-remixes",
        "https://kpopping.com/community"
    ]
    for u in urls:
        await test_page(u)

if __name__ == "__main__":
    asyncio.run(main())
