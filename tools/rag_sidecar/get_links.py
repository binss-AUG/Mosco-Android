import httpx
import re
from bs4 import BeautifulSoup
import asyncio

async def fetch():
    async with httpx.AsyncClient(headers={"User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36"}) as client:
        resp = await client.get('https://kpopping.com/profiles/group/tripleS', follow_redirects=True)
        soup = BeautifulSoup(resp.text, 'lxml')
        for a in soup.find_all('a', href=True):
            if 'idol/' in a['href'] or 'group/' in a['href']:
                print(a['href'])

asyncio.run(fetch())
