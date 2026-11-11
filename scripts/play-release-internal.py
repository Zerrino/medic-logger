#!/usr/bin/env python3
"""Upload the release AAB and roll it out on the INTERNAL track in one uninterrupted Play edit.
Run: uv run --with google-auth --with requests python scripts/play-release-internal.py <aab> <versionCode> <name> [--dry]
Play keeps a single open edit per app: touching Play Console while this runs can delete it."""
import json, sys, google.auth.transport.requests as tr
from google.oauth2 import service_account
PKG="fr.mediclogger.app"
aab,code,name=sys.argv[1],sys.argv[2],sys.argv[3]; dry="--dry" in sys.argv
NOTES={"en-US":"- New dark theme that follows your system setting.\n- Optional tips through Google Play (Settings > Support).\n- Better TalkBack support: buttons and tabs are announced correctly.\n- Smoother screens; exports keep going if the screen is recreated.\n- Fixed a possible crash when settings could not be saved."}
creds=service_account.Credentials.from_service_account_file("/Users/alexafer/.config/play-console-mcp/service-account.json",scopes=["https://www.googleapis.com/auth/androidpublisher"])
s=tr.AuthorizedSession(creds)
api=f"https://androidpublisher.googleapis.com/androidpublisher/v3/applications/{PKG}"
def ok(r,what):
    print(what,r.status_code)
    if r.status_code>=300: print(r.text[:500]); sys.exit(1)
    return r.json() if r.text else {}
edit=ok(s.post(api+"/edits",json={}),"insert edit")["id"]
up=f"https://androidpublisher.googleapis.com/upload/androidpublisher/v3/applications/{PKG}/edits/{edit}/bundles?uploadType=media"
with open(aab,"rb") as f:
    r=s.post(up,data=f,headers={"Content-Type":"application/octet-stream"})
ok(r,"upload bundle")
ok(s.put(api+f"/edits/{edit}/tracks/internal",json={"track":"internal","releases":[{"name":name,"versionCodes":[code],"status":"completed","releaseNotes":[{"language":l,"text":t} for l,t in NOTES.items()]}]}),"set internal track")
ok(s.post(api+f"/edits/{edit}:validate"),"validate")
if dry: print("dry run: not committed"); sys.exit()
ok(s.post(api+f"/edits/{edit}:commit"),"commit")
print("committed internal release",name,code)
