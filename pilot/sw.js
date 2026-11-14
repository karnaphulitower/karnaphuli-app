const C='kt-1794635976';self.addEventListener('install',e=>{self.skipWaiting();e.waitUntil(caches.open(C).then(c=>c.addAll(['./','index.html','manifest.json','firebase-app-compat.js','firebase-auth-compat.js','firebase-firestore-compat.js'])))});
self.addEventListener('activate',e=>e.waitUntil(self.clients.claim()));
self.addEventListener('push',e=>{let d={};try{d=e.data?e.data.json():{}}catch(x){d={title:'কর্ণফুলী টাওয়ার',body:e.data?e.data.text():''}}
 const sos=d.t==='sos',call=d.t==='call';const hh=new Date(Date.now()+216e5).getUTCHours();const qt=!sos&&!call&&(hh>=23||hh<8);
 e.waitUntil(self.clients.matchAll({type:'window',includeUncontrolled:true}).then(cs=>{if(cs.some(c=>c.visibilityState==='visible'&&c.focused))return;
  return self.registration.showNotification(d.title||'কর্ণফুলী টাওয়ার',{body:d.body||'',tag:d.tag||'kt',renotify:true,requireInteraction:sos||call,icon:'icon-192.png',badge:'icon-192.png',
   vibrate:sos?[1000,300,1000,300,1000,300,1000,300,1000]:call?[700,400,700,400,700,400,700]:qt?[]:[200],silent:qt,data:d,actions:call?[{action:'acc',title:'📞 ধরুন'}]:[]})}))});
self.addEventListener('notificationclick',e=>{const d=e.notification.data||{};e.notification.close();const acc=e.action==='acc'||d.t==='call';
 const url='./'+(d.t==='call'?(acc?'?acc='+d.id:'?call='+d.id):d.t==='sos'?'?sos='+d.id:'');
 e.waitUntil(self.clients.matchAll({type:'window',includeUncontrolled:true}).then(cs=>{const c=cs[0];if(c){if(acc&&d.id)c.postMessage({acc:d.id});return c.focus()}return self.clients.openWindow(url)}))});
self.addEventListener('fetch',e=>{const u=new URL(e.request.url);if(e.request.method!=='GET'||u.origin!==location.origin)return;e.respondWith(fetch(e.request,(e.request.mode==='navigate'||/\.html$|\/$|sw\.js$|manifest\.json$/.test(u.pathname))?{cache:'no-store'}:{}).then(r=>{const k=r.clone();caches.open(C).then(c=>c.put(e.request,k));return r}).catch(()=>caches.match(e.request).then(r=>r||caches.match('index.html'))))});