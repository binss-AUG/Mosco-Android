import json
with open('server/data/assets/database.json', 'r', encoding='utf-8') as f:
    db = json.load(f)
collections = db.get('collections', [])
first_seoyeon = [c for c in collections if c.get('member') == 'SeoYeon' and 'First' in c.get('class', '')]
print("Seoyeon First cards:", len(first_seoyeon))
if len(first_seoyeon) > 0:
    print(first_seoyeon[0])
