import json
with open('test_open.json', 'r', encoding='utf-16') as f:
    data = json.load(f)
cards = data.get('data', {}).get('cards', [])
print("Total cards returned:", len(cards))
members = {}
for c in cards:
    member = c.get('cardData', {}).get('member', 'Unknown')
    members[member] = members.get(member, 0) + 1
print("Members distribution:", members)
