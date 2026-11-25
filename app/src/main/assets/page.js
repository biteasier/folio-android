window.folio=(function(){
var M=document.querySelector('main');
document.querySelectorAll('main table').forEach(function(t){var d=document.createElement('div');d.className='tw';t.parentNode.insertBefore(d,t);d.appendChild(t)});
function off(i){var s=document.createElement('span');s.className='off';s.textContent='Image: '+(i.alt||decodeURIComponent((i.getAttribute('src')||'').split('/').pop().slice(0,80).replace(/%(?![0-9a-f]{2})/gi,'%25')));
var n=s;if(/^https?:/i.test(i.src)&&i.src.indexOf(location.origin)!==0&&!i.closest('a')){n=document.createElement('a');n.href=i.src;n.appendChild(s)}i.replaceWith(n)}
document.querySelectorAll('main img').forEach(function(i){var r=i.src.indexOf('data:')!==0&&i.src.indexOf(location.origin)!==0;if(r||(i.complete&&!i.naturalWidth)){try{off(i)}catch(e){}}else i.addEventListener('error',function(){try{off(i)}catch(e){}})});
function heads(){return Array.prototype.slice.call(document.querySelectorAll('main h1,main h2,main h3,main h4,main h5,main h6'))}
return{
toc:function(){return heads().map(function(h){return[+h.tagName[1],h.textContent.trim().slice(0,300)||'(untitled)']})},
now:function(){var a=heads(),c=-1;for(var i=0;i<a.length;i++){if(a[i].getBoundingClientRect().top<=60)c=i}return c},
go:function(n){var h=heads()[n];if(h){var d=h.closest('details');if(d)d.open=true;h.scrollIntoView();scrollBy(0,-8)}},
anchor:function(a){var e=document.getElementById(a)||document.getElementsByName(a)[0];if(e){e.scrollIntoView();scrollBy(0,-8)}},
pos:function(){if(scrollY<8)return'';var k=M.children,a=0,b=k.length-1;while(a<b){var m=(a+b)>>1;if(k[m].getBoundingClientRect().bottom<=0)a=m+1;else b=m}if(!k[a])return'';var r=k[a].getBoundingClientRect();return a+':'+(r.height?Math.max(0,-r.top/r.height):0).toFixed(5)},
restore:function(p){var q=p.split(':'),e=M.children[+q[0]],n=0;if(!e)return;(function f(){var r=e.getBoundingClientRect();scrollTo(0,scrollY+r.top+r.height*+q[1]);if(++n<6)requestAnimationFrame(f)})()},
set:function(t,f,w){var d=document.documentElement.dataset;d.theme=t;d.font=f;if(w)d.wrap='1';else delete d.wrap},
keep:function(g){var p=this.pos();g();if(p)this.restore(p)}
}})();
