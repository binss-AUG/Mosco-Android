import os
import re

def cleanup_file(filepath):
    with open(filepath, 'r', encoding='utf-8') as f:
        content = f.read()

    # 1. Remove ivBackground, driftX, driftY declarations (all variants)
    content = re.sub(r'private\s+ImageView\s+ivBackground;\s*', '', content)
    content = re.sub(r'private\s+ObjectAnimator\s+driftX,\s+driftY;\s*', '', content)
    
    # 2. Wipe the entire setupAmbientEffects method content
    content = re.sub(r'private\s+void\s+setupAmbientEffects\s*\([\s\S]*?\)\s*\{[\s\S]*?\}', 
                     'private void setupAmbientEffects(long playTimeX, long playTimeY) {\n        // GalacticBackgroundView handles its own animation now.\n    }', content)
    
    # 3. Remove intent.putExtra blocks for EXTRA_PLAY_TIME (more robust regex)
    content = re.sub(r'if\s*\(driftX\s*!=\s*null\s*&&\s*driftY\s*!=\s*null\)\s*\{[\s\S]*?\}', '', content)
    
    # 4. Remove extra playTime long variables
    content = re.sub(r'long\s+playTimeX\s*=\s*getIntent\(\)\.getLongExtra\("EXTRA_PLAY_TIME_X",\s*0L\);', '', content)
    content = re.sub(r'long\s+playTimeY\s*=\s*getIntent\(\)\.getLongExtra\("EXTRA_PLAY_TIME_Y",\s*0L\);', '', content)

    # 5. Fix remaining manual ivBackground references in other methods if any
    content = re.sub(r'ivBackground\s*=\s*findViewById\(R\.id\.iv_background_parallax\);', '', content)

    with open(filepath, 'w', encoding='utf-8') as f:
        f.write(content)

files = [
    r'D:\MEox\UITer\DOAN\Mosco_Megre\Mosco\client\app\src\main\java\com\vn\jet\mosco\OnboardingActivity.java',
    r'D:\MEox\UITer\DOAN\Mosco_Megre\Mosco\client\app\src\main\java\com\vn\jet\mosco\SignInActivity.java',
    r'D:\MEox\UITer\DOAN\Mosco_Megre\Mosco\client\app\src\main\java\com\vn\jet\mosco\SignUpActivity.java',
    r'D:\MEox\UITer\DOAN\Mosco_Megre\Mosco\client\app\src\main\java\com\vn\jet\mosco\ForgotPasswordActivity.java',
    r'D:\MEox\UITer\DOAN\Mosco_Megre\Mosco\client\app\src\main\java\com\vn\jet\mosco\DisplayNameSetupActivity.java',
    r'D:\MEox\UITer\DOAN\Mosco_Megre\Mosco\client\app\src\main\java\com\vn\jet\mosco\SplashActivity.java'
]

for f in files:
    if os.path.exists(f):
        cleanup_file(f)
        print(f"Cleaned up {f}")
