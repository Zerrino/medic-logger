#!/usr/bin/env python3
"""Create the three tip products in Play Console (needs a payments profile first).
Run: uv run --with google-auth --with requests python scripts/create-play-tips.py create
Uses the service account key at ~/.config/play-console-mcp/service-account.json (never commit it)."""
from google.oauth2 import service_account
PKG="fr.mediclogger.app"
creds=service_account.Credentials.from_service_account_file("/Users/alexafer/.config/play-console-mcp/service-account.json",scopes=["https://www.googleapis.com/auth/androidpublisher"])
s=tr.AuthorizedSession(creds)
base=f"https://androidpublisher.googleapis.com/androidpublisher/v3/applications/{PKG}"
def call(m,url,**kw):
    import time
    for _ in range(5):
        r=s.request(m,url,**kw)
        if r.status_code not in (500,503): break
        time.sleep(4)
    print(m,url.split("v3/")[-1][:90],r.status_code); 
    try: return r.status_code,r.json()
    except Exception: return r.status_code,r.text
TIPS=[("tip.small","Small coffee","Optional tip to support Medic Logger. Unlocks nothing.",990000000,"EUR",0,990000000),
      ("tip.medium","Large coffee","Optional tip to support Medic Logger. Unlocks nothing.",None,"EUR",2,990000000),
      ("tip.large","Coffee and croissant","Optional tip to support Medic Logger. Unlocks nothing.",None,"EUR",4,990000000)]
mode=sys.argv[1]
if mode=="list":
    print(json.dumps(call("GET",base+"/onetimeproducts")[1],indent=1)[:1500]); sys.exit()
for suffix,title,desc,_,cur,units,nanos in TIPS:
    pid=f"{PKG}.{suffix}"
    code,conv=call("POST",base+"/pricing:convertRegionPrices",json={"price":{"currencyCode":cur,"units":str(units),"nanos":nanos}})
    if code!=200: print(conv); sys.exit(1)
    regional=[{"regionCode":rc,"price":v["price"],"availability":"AVAILABLE"} for rc,v in conv["convertedRegionPrices"].items()]
    new=conv["convertedOtherRegionsPrice"]
    body={"packageName":PKG,"productId":pid,
      "listings":[{"languageCode":"en-US","title":title,"description":desc}],
      "purchaseOptions":[{"purchaseOptionId":"standard","buyOption":{"legacyCompatible":True,"multiQuantityEnabled":suffix=="tip.small"},
        "regionalPricingAndAvailabilityConfigs":regional,
        "newRegionsConfig":{"usdPrice":new["usdPrice"],"eurPrice":new["eurPrice"],"availability":"AVAILABLE"}}]}
    code,res=call("PATCH",base+f"/onetimeproducts/{pid}",params={"updateMask":"listings,purchaseOptions","allowMissing":"true","regionsVersion.version":conv["regionVersion"]["version"]},json=body)
    print(json.dumps(res)[:400])
