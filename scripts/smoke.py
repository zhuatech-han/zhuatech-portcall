#!/usr/bin/env python3
# Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2
"""Real HTTP/MySQL acceptance with synthetic TEST data; private credentials never printed."""
from pathlib import Path
import argparse,concurrent.futures,http.cookiejar,json,os,secrets,urllib.request,urllib.error,uuid
ROOT=Path(__file__).resolve().parents[1];STATE=ROOT/'output/qa-state.json';BASE=os.environ.get('TEST_URL','http://127.0.0.1:8128').rstrip('/');checks=0

def check(ok,message):
    """Verify invariant without emitting a credential or response body."""
    global checks
    checks+=1
    if not ok:raise AssertionError(message)

def key():return str(uuid.uuid4())

class Client:
    """Isolated cookie session with real CSRF on writes."""
    def __init__(self,name,password):
        self.opener=urllib.request.build_opener(urllib.request.HTTPCookieProcessor(http.cookiejar.CookieJar()))
        self.csrf=self.request('/auth/csrf');self.profile=self.request('/auth/login','POST',{'username':name,'password':password})
    def request(self,path,method='GET',data=None,status=200,code=None,csrf=True):
        headers={'Content-Type':'application/json'}
        if method!='GET' and csrf and hasattr(self,'csrf'):headers[self.csrf['header']]=self.csrf['token']
        req=urllib.request.Request(BASE+'/api'+path,data=None if data is None else json.dumps(data).encode(),headers=headers,method=method)
        try:
            with self.opener.open(req,timeout=30) as res:actual=res.status;value=json.load(res)
        except urllib.error.HTTPError as e:actual=e.code;value=json.load(e)
        check(actual in status if isinstance(status,tuple) else actual==status,f'{method} {path}: expected {status}, got {actual}, code={value.get("code") if isinstance(value,dict) else None}')
        if code:check(value.get('code')==code,path+': wrong error')
        return value

def command(who,kind,id,action,extra=None,status=200,code=None,body=None):
    """Apply one versioned state command with an explicit evidence note."""
    value=body or {'requestKey':key(),'version':admin.request(f'/{kind}/{id}')['record']['version'],'note':'TEST verified operational evidence'}
    value.update(extra or {});return who.request(f'/{kind}/{id}/commands/{action}','POST',value,status,code)

def capture(state):
    """Persist only private acceptance snapshots for restart and restore comparison."""
    paths=['/admin/users','/admin/roles','/admin/departments','/admin/permissions','/admin/menus','/admin/settings','/admin/dictionaries','/options','/dashboard']
    for kind in ['calls','changes','services','vessels','agencies']:
        paths.append('/'+kind+'?size=100&sort=reference')
        if kind in ['calls','changes','services']:paths.extend(f'/{kind}/{r["id"]}' for r in admin.request('/'+kind+'?size=100')['items'])
    return {p:admin.request(p) for p in paths}

# Synthetic TEST actors and operational times only; this script must use a disposable own database.
from datetime import datetime,timezone,timedelta
parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--allow-test-writes',action='store_true');parser.add_argument('--verify',action='store_true');parser.add_argument('--capture',action='store_true');args=parser.parse_args()
env=dict(line.split('=',1) for line in (ROOT/'.env').read_text().splitlines() if '=' in line and not line.startswith('#'));admin=Client('admin',env['ADMIN_PASSWORD'])
if args.verify or args.capture:
 state=json.loads(STATE.read_text());current=capture(state)
 if args.capture:
  state['responses']=current;STATE.write_text(json.dumps(state,ensure_ascii=False));print(json.dumps({'mode':'capture','responses':len(current),'result':'PASS'}));raise SystemExit
 for p,expected in state['responses'].items():check(current[p]==expected,'Persistence mismatch: '+p)
 for name,username in state['users'].items():check(Client(username,state['password']).request('/auth/me')['username']==username,'Actor missing: '+name)
 print(json.dumps({'mode':'persistence','assertions':checks,'responsesMatched':len(current),'result':'PASS'}));raise SystemExit
if not args.allow_test_writes:raise SystemExit('Use --allow-test-writes only with a disposable isolated database.')
if STATE.exists():raise SystemExit('QA state exists; use --verify or a new isolated test database.')
suffix=secrets.token_hex(4);password='Aa9'+secrets.token_urlsafe(24);users={};clients={};userIds={}
roles={r['name']:r['id'] for r in admin.request('/admin/roles')};dep=admin.request('/admin/departments','POST',{'name':'TEST 港口协同 '+suffix})['id'];outsideDep=admin.request('/admin/departments','POST',{'name':'TEST 外部部门 '+suffix})['id']
def master(name,kind='',department=dep):return {'requestKey':key(),'reference':'TEST-'+key()[:8],'name':name,'kind':kind,'departmentId':department,'enabled':True}
agentId=admin.request('/agencies','POST',master('TEST 上海船代 '+suffix,'AGENT'))['id'];secondAgent=admin.request('/agencies','POST',master('TEST 另一船代 '+suffix,'AGENT'))['id'];providerId=admin.request('/agencies','POST',master('TEST 码头服务 '+suffix,'PROVIDER'))['id'];otherId=admin.request('/agencies','POST',master('TEST 补给服务 '+suffix,'PROVIDER'))['id']
selfRole=admin.request('/admin/roles','POST',{'name':'TEST 本人挂靠 '+suffix,'scope':'SELF','permissions':['catalog.write','call.read','call.write','change.read','change.write','service.read','service.write','dashboard','export','audit']})['id']
actorNames={'ops':'挂靠协调','review':'独立复核','agent':'上海船代','otherAgent':'另一船代','provider':'码头服务','otherProvider':'补给服务','bound':'绑定机构管理员','outside':'外部部门','self':'本人挂靠'}
for name,role,department,binding in [('ops',roles['挂靠协调'],dep,None),('review',roles['独立复核'],dep,None),('agent',roles['船舶代理'],dep,agentId),('otherAgent',roles['船舶代理'],dep,secondAgent),('provider',roles['服务提供方'],dep,providerId),('otherProvider',roles['服务提供方'],dep,otherId),('bound',roles['管理员'],dep,providerId),('outside',roles['挂靠协调'],outsideDep,None),('self',selfRole,dep,None)]:
 username='test-'+name+'-'+suffix;users[name]=username;userIds[name]=admin.request('/admin/users','POST',{'username':username,'displayName':'TEST '+actorNames[name],'password':password,'roleId':role,'departmentId':department,'agencyId':binding,'enabled':True})['id'];clients[name]=Client(username,password)
ops=clients['ops'];review=clients['review'];agent=clients['agent'];provider=clients['provider'];shipId=ops.request('/vessels','POST',master('TEST SEA LIGHT '+suffix))['id']
now=datetime.now(timezone.utc).replace(microsecond=0)
def at(seconds):return (now+timedelta(seconds=seconds)).isoformat().replace('+00:00','Z')
eta=at(-10800);etd=at(86400);calls=[];services=[];changes=[]
def call(who=agent):
 c=who.request('/calls','POST',{'requestKey':key(),'reference':'TEST-CALL-'+key()[:8],'vesselId':shipId,'agentId':agentId,'location':'TEST 上海港区 A','eta':eta,'etd':etd})['record'];calls.append(c['id']);return c['id']
def planned(id):command(agent,'calls',id,'submit');command(review,'calls',id,'approve')
def service(id,p=providerId,critical=True):
 s=ops.request('/services','POST',{'requestKey':key(),'reference':'TEST-SERVICE-'+key()[:8],'callId':id,'providerId':p,'kind':'CARGO' if p==providerId else 'SUPPLIES','critical':critical,'windowStart':at(-10000),'windowEnd':at(-6000)})['record'];services.append(s['id']);return s['id']
def change(id):
 c=admin.request('/calls/'+str(id))['record'];x=agent.request('/changes','POST',{'requestKey':key(),'reference':'TEST-CHANGE-'+key()[:8],'callId':id,'baseRevision':c['planRevision'],'eta':at(-7200),'etd':at(90000)})['record'];changes.append(x['id']);return x['id']
# Initial planning and real provider confirmations.
a=call();planned(a);sa=service(a);sb=service(a,otherId,False)
command(ops,'services',sa,'request');command(provider,'services',sa,'confirm');command(ops,'services',sb,'request');command(clients['otherProvider'],'services',sb,'confirm')
# Exact retries bind actor/action/payload and leave the event count unchanged.
b=call();body={'requestKey':key(),'version':1,'note':'TEST request retry'};command(agent,'calls',b,'submit',body=body.copy());before=admin.request(f'/calls/{b}');command(agent,'calls',b,'submit',body=body.copy());check(admin.request(f'/calls/{b}')==before,'Retry changed call or evidence');command(agent,'calls',b,'submit',body={**body,'note':'TEST altered'},status=409,code='REQUEST_KEY_REUSED');command(admin,'calls',b,'submit',body=body.copy(),status=409,code='REQUEST_KEY_REUSED');command(agent,'calls',b,'submit',body={'requestKey':key(),'version':1,'note':'TEST stale'},status=409,code='STALE_VERSION');command(review,'calls',b,'approve')
# A plan change invalidates every not-yet-performed request, preserves the old evidence and needs reconfirmation.
x=change(a);command(agent,'changes',x,'submit');command(review,'changes',x,'approve');check(admin.request(f'/calls/{a}')['record']['planRevision']==2,'Plan revision not advanced')
for sid,who in [(sa,provider),(sb,clients['otherProvider'])]:
 check(admin.request(f'/services/{sid}')['record']['status']=='CHANGE_PENDING','Old service confirmation survived change');command(who,'services',sid,'confirm',status=409,code='INVALID_STATE');command(ops,'services',sid,'replan',{'windowStart':at(-7000),'windowEnd':at(-5000)});command(who,'services',sid,'confirm');check(admin.request(f'/services/{sid}')['record']['planRevision']==2,'Service has old plan revision')
# Bound ALL provider still cannot read another provider, agent changes, internal directories or other calls.
clients['bound'].request(f'/services/{sb}',status=403,code='OUT_OF_SCOPE');clients['bound'].request('/admin/users',status=403,code='STAFF_ONLY');clients['bound'].request('/audit',status=403,code='STAFF_ONLY');clients['bound'].request('/changes',status=403,code='AGENT_ONLY');clients['bound'].request('/calls','POST',{},403,'AGENT_ONLY');boundCall=clients['bound'].request(f'/calls/{a}');check([s['id'] for s in boundCall['services']]==[sa] and 'changes' not in boundCall,'Provider call detail leaked other work');check(clients['provider'].request('/services?size=100')['total']==1,'Provider list leak');check('changes' not in [m['code'] for m in clients['bound'].request('/auth/me')['menus']],'Provider internal menu leak')
for name in ['otherAgent','outside','self']:
 clients[name].request(f'/calls/{a}',status=403,code='OUT_OF_SCOPE');clients[name].request(f'/calls/{a}/report.json',status=403,code='OUT_OF_SCOPE');check(clients[name].request('/calls?size=100')['total']==0,'Call range leak '+name)
clients['otherProvider'].request(f'/services/{sa}',status=403,code='OUT_OF_SCOPE');command(admin,'services',sa,'start',{'at':at(-6000)},403,'BOUND_PROVIDER_REQUIRED');check(clients['outside'].request('/dashboard')['calls']==0,'Department dashboard leak');check('passwordHash' not in json.dumps(admin.request('/admin/users')),'Password hash leak');check('zhuatech2' not in json.dumps(provider.request(f'/calls/{a}/report.json')),'Advertisement in export')
# Failed future milestone rolls back and the same UUID can carry the corrected input.
body={'requestKey':key(),'version':admin.request(f'/calls/{a}')['record']['version'],'note':'TEST arrival proof','at':at(3600)};command(ops,'calls',a,'arrive',body=body.copy(),status=400,code='INVALID_ACTUAL_TIME');check(admin.request(f'/calls/{a}')['record']['arrivedAt'] is None,'Failed arrival persisted');command(ops,'calls',a,'arrive',body={**body,'at':at(-6600)});command(provider,'services',sa,'start',{'at':at(-6500)});command(provider,'services',sa,'report',{'at':at(-6000)});command(review,'services',sa,'dispute');command(provider,'services',sa,'report',{'at':at(-5900)});command(review,'services',sa,'accept');command(clients['otherProvider'],'services',sb,'start',{'at':at(-6400)})
# Actual departure may be recorded while a report is outstanding; close remains blocked.
command(ops,'calls',a,'depart',{'at':at(-5500)});command(review,'calls',a,'close',status=409,code='UNRESOLVED_WORK');command(clients['otherProvider'],'services',sb,'report',{'at':at(-5400)},400,'AFTER_DEPARTURE');command(clients['otherProvider'],'services',sb,'report',{'at':at(-5600)});command(review,'services',sb,'accept');command(review,'calls',a,'close');check(admin.request(f'/calls/{a}')['record']['status']=='CLOSED','Full call not closed');command(ops,'services',sa,'cancel',status=409,code='INVALID_STATE');command(agent,'calls',a,'cancel',status=409,code='INVALID_STATE')
# Concurrent change approvals advance the call once; old confirmations become CHANGE_PENDING.
sc=service(b);command(ops,'services',sc,'request');command(provider,'services',sc,'confirm');xc=change(b);command(agent,'changes',xc,'submit');version=admin.request(f'/changes/{xc}')['record']['version']
def competing():return command(Client(users['review'],password),'changes',xc,'approve',body={'requestKey':key(),'version':version,'note':'TEST concurrent review'},status=(200,409))
with concurrent.futures.ThreadPoolExecutor(2) as ex:
 one=ex.submit(competing);two=ex.submit(competing);results=[one.result(),two.result()]
check(sum('record' in r for r in results)==1,'Concurrent change approved twice');check(admin.request(f'/calls/{b}')['record']['planRevision']==2,'Concurrent revision wrong');check(admin.request(f'/services/{sc}')['record']['status']=='CHANGE_PENDING','Concurrent change left old confirmation');command(agent,'calls',b,'cancel');check(admin.request(f'/services/{sc}')['record']['status']=='CANCELLED','Call cancellation omitted service')
# Request refusal, independent acceptance and call completeness are controlled separately.
c=call();planned(c);sd=service(c);command(ops,'services',sd,'request');command(provider,'services',sd,'decline');command(ops,'calls',c,'arrive',{'at':at(-6200)});command(ops,'calls',c,'depart',{'at':at(-5000)});command(review,'calls',c,'close',status=409,code='UNRESOLVED_WORK');command(ops,'services',sd,'cancel');command(review,'calls',c,'close',status=409,code='UNRESOLVED_WORK')
# Editable drafts and noninterchangeable institutions.
d=call();draft=admin.request(f'/calls/{d}')['record'];agent.request(f'/calls/{d}','PUT',{'requestKey':key(),'version':draft['version'],'reference':draft['reference'],'vesselId':shipId,'agentId':agentId,'location':'TEST 上海港区 B','eta':eta,'etd':etd});command(agent,'calls',d,'cancel');temp=ops.request('/agencies','POST',master('TEST 未引用服务商','PROVIDER'));ops.request('/agencies/'+str(temp['id']),'DELETE',{});ops.request('/vessels/'+str(shipId),'DELETE',{},409,'CONFLICT');ops.request('/agencies/'+str(agentId),'DELETE',{},409,'CONFLICT')
# Range, malformed time, registered settings and session revocation.
ops.request('/calls?size=101',status=400,code='INVALID_PAGE');ops.request('/calls?sort=sql',status=400,code='INVALID_PAGE');ops.request('/calls','POST',{},403,csrf=False);check(agent.request('/calls?status=CLOSED')['total']==1,'State filter');check(provider.request('/services?kind=CARGO')['total']==3,'Service type filter');check(provider.request('/services?size=1')['total']==3,'Pagination count');check('total' not in provider.request('/dashboard'),'Unexpected global inventory')
user=next(v for v in admin.request('/admin/users') if v['id']==userIds['outside']);admin.request('/admin/users/'+str(user['id']),'PUT',{**user,'enabled':False});clients['outside'].request('/auth/me',status=401,code='UNAUTHENTICATED');admin.request('/admin/users/'+str(user['id']),'PUT',{**user,'enabled':True})
# A self-scoped internal actor creates and only sees their own call.
e=call(clients['self']);check(clients['self'].request('/calls?size=100')['total']==1,'Self call list');command(clients['self'],'calls',e,'submit');command(review,'calls',e,'approve');command(clients['self'],'calls',e,'cancel')
STATE.parent.mkdir(exist_ok=True);state={'password':password,'users':users,'userIds':userIds,'departmentId':dep,'shipId':shipId,'agentId':agentId,'providerId':providerId,'otherProviderId':otherId,'callIds':calls,'serviceIds':services,'changeIds':changes};state['responses']=capture(state)
fd=os.open(STATE,os.O_WRONLY|os.O_CREAT|os.O_EXCL,0o600)
with os.fdopen(fd,'w') as out:json.dump(state,out,ensure_ascii=False)
print(json.dumps({'mode':'real-http-mysql','assertions':checks,'calls':len(calls),'services':len(services),'changes':len(changes),'result':'PASS'}))
