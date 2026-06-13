import os

d = 'client/app/src/main/java'
patterns = [
    '"Motion".equalsIgnoreCase(selectedObjet.getCardClass()) && ',
    '"Motion".equalsIgnoreCase(cardClass) && ',
    '"Motion".equalsIgnoreCase(resultClass) && ',
    '"Motion".equalsIgnoreCase(selectedObj.getCardClass()) && ',
    '"Motion".equalsIgnoreCase(mainCard.getCardClass()) && ',
    '"Motion".equalsIgnoreCase(entry.getCardClass()) && '
]

for r, _, fs in os.walk(d):
    for f in fs:
        if f.endswith('.java'):
            p = os.path.join(r, f)
            with open(p, 'r', encoding='utf-8') as file:
                content = file.read()
            original = content
            for pat in patterns:
                content = content.replace(pat, '')
            if original != content:
                with open(p, 'w', encoding='utf-8') as file:
                    file.write(content)
                print('Updated ' + f)
