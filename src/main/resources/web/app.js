"use strict";
const $ = (tag, cls, text) => { const e=document.createElement(tag); if(cls)e.className=cls;if(text!==undefined)e.textContent=text;return e; };
const root=document.getElementById("app");
const state={me:null,workspaces:[],workspace:null,projects:[],page:"dashboard",project:null,tab:"tasks",timer:null};
async function api(path,method="GET",data){
  const options={method,headers:{}};
  if(data!==undefined){options.headers["Content-Type"]="application/json";options.body=JSON.stringify(data);}
  const r=await fetch("/api"+path,options);
  const result=await r.json().catch(()=>({error:"invalid_response"}));
  if(!r.ok)throw new Error(result.error||"request_failed");
  return result;
}

const GLYPHS={
grid:'<rect x="3" y="3" width="7" height="7" rx="1"/><rect x="14" y="3" width="7" height="7" rx="1"/><rect x="3" y="14" width="7" height="7" rx="1"/><rect x="14" y="14" width="7" height="7" rx="1"/>',
folder:'<path d="M3 7a2 2 0 0 1 2-2h5l2 2h7a2 2 0 0 1 2 2v10H3z"/>',
tasks:'<rect x="4" y="4" width="16" height="16" rx="2"/><path d="m8 12 2.5 2.5L16 9"/>',
clock:'<circle cx="12" cy="12" r="9"/><path d="M12 7v5l3 2"/>',
users:'<circle cx="9" cy="8" r="3"/><path d="M3 20v-2a6 6 0 0 1 12 0v2M16 5a3 3 0 0 1 0 6M18 14a5 5 0 0 1 3 5"/>',
settings:'<circle cx="12" cy="12" r="3"/><path d="M12 2v3m0 14v3M2 12h3m14 0h3M5 5l2 2m10 10 2 2M19 5l-2 2M7 17l-2 2"/>',
plus:'<path d="M12 4v16M4 12h16"/>',
check:'<circle cx="12" cy="12" r="9"/><path d="m7 12 3 3 7-7"/>',
activity:'<path d="M2 12h4l3-7 5 14 3-7h5"/>',
arrow:'<path d="M5 12h14m-6-6 6 6-6 6"/>',
search:'<circle cx="11" cy="11" r="7"/><path d="m16 16 5 5"/>',
spark:'<path d="m12 2 2.5 7.5L22 12l-7.5 2.5L12 22l-2.5-7.5L2 12l7.5-2.5z"/>'
};
function glyph(name,cls="ui-icon"){const svg=document.createElementNS("http://www.w3.org/2000/svg","svg");svg.setAttribute("viewBox","0 0 24 24");svg.setAttribute("class",cls);svg.setAttribute("fill","none");svg.setAttribute("stroke","currentColor");svg.setAttribute("stroke-width","1.8");svg.setAttribute("stroke-linecap","round");svg.setAttribute("stroke-linejoin","round");svg.innerHTML=GLYPHS[name]||GLYPHS.grid;return svg;}
function withIcon(label,iconName,handler,css="btn"){const b=button("",handler,css);b.append(glyph(iconName),$("span","",label));return b;}
function projectDialog(){dialog("Novo projeto",[{key:"name",label:"Nome do projeto"},{key:"description",label:"Descrição (opcional)",optional:true}],v=>api("/projects?workspaceId="+state.workspace,"POST",v));}
function taskDialog(projectId){dialog("Nova tarefa",[{key:"title",label:"O que é preciso fazer?"},{key:"dueDate",label:"Prazo AAAA-MM-DD (opcional)",optional:true}],v=>api("/projects/"+projectId+"/tasks","POST",v));}
function chooseTaskProject(){
 if(!state.projects.length){navigate("projects");projectDialog();return;}
 if(state.projects.length===1){taskDialog(state.projects[0].id);return;}
 const shade=$("div","dialog"),box=$("div","box");box.append($("h2","","Em que projeto?"),$("p","muted","Escolhe a pasta onde queres adicionar a tarefa."));
 for(const p of state.projects){box.append(button(p.name,()=>{shade.remove();taskDialog(p.id);},"project-choice"));}
 box.append(button("Cancelar",()=>shade.remove(),"btn ghost"));shade.append(box);document.body.append(shade);
}
function emptyState(iconName,title,description,actionLabel,action){
 const box=$("div","empty-state");box.append(glyph(iconName,"empty-icon"),$("h3","",title),$("p","muted",description));
 if(actionLabel)box.append(withIcon(actionLabel,"plus",action));return box;
}
function taskRow(t,p){
 const row=$("div","task-row"),label=$("label","task-main"),check=$("input");check.type="checkbox";check.checked=t.status==="DONE";check.setAttribute("aria-label","Marcar "+t.title+" como concluída");
 check.onchange=async()=>{check.disabled=true;try{await api("/tasks/"+t.id,"PATCH",{status:check.checked?"DONE":"TODO"});await refresh();}catch(ex){check.checked=!check.checked;check.disabled=false;error(ex);}};
 label.append(check,$("span","task-name",t.title));row.append(label,$("span","task-project",p.name));
 if(t.priority==="HIGH")row.append($("span","priority high","Alta"));
 if(t.dueDate)row.append($("span","task-date",t.dueDate));
 return row;
}

const msg=e=>{const t=$("p","error",e.message||String(e));return t};
function button(label,fn,cls="btn"){const b=$("button",cls,label);b.type="button";b.onclick=()=>Promise.resolve(fn()).catch(error);return b}
function error(ex){let bar=document.getElementById("errorbar");if(!bar){bar=$("div","error");bar.id="errorbar";document.body.prepend(bar);}bar.textContent="Erro: "+(ex.message||ex);setTimeout(()=>{if(bar.isConnected)bar.remove()},5000);}
function clear(){if(state.timer){clearInterval(state.timer);state.timer=null;}root.replaceChildren();}
function input(placeholder,type="text"){const i=$("input");i.placeholder=placeholder;i.type=type;i.required=true;return i;}
function dialog(title,fields,submit){
  const overlay=$("div","dialog"),box=$("div","box"),head=$("div","row"),form=$("form","form");
  head.append($("h2","",title),button("✕",()=>overlay.remove(),"btn ghost"));box.append(head);
  let entries={};for(const f of fields){const i=input(f.label,f.type||"text");if(f.optional)i.required=false;entries[f.key]=i;form.append(i);}
  const actions=$("div","row");actions.append(button("Cancelar",()=>overlay.remove(),"btn ghost"));
  const go=$("button","btn","Guardar");go.type="submit";actions.append(go);form.append(actions);
  form.onsubmit=async e=>{e.preventDefault();go.disabled=true;try{const values={};for(const f of fields)values[f.key]=entries[f.key].value.trim();await submit(values);overlay.remove();await refresh();}catch(ex){error(ex);}finally{go.disabled=false;}};
  box.append(form);overlay.append(box);document.body.append(overlay);
  const first=Object.values(entries)[0];if(first)first.focus();
}
async function boot(){
  try{state.me=await api("/me");await loadWorkspaces();}catch(ex){if(ex.message==="login_required"){auth();return;}error(ex);auth();return;}
  render();
}
function auth(){
  clear();let isRegister=false;
  const wrap=$("div","auth"),box=$("div","box");wrap.append(box);root.append(wrap);
  function form(){
    box.replaceChildren();box.append($("h1","","Nord Workspace"),$("p","muted","Os teus projetos, tarefas e equipa num só lugar."));
    const f=$("form","form"),name=input("Nome"),email=input("Email","email"),password=input("Password (mínimo 12 caracteres)","password");
    if(isRegister)f.append(name);f.append(email,password);
    const submit=$("button","btn",isRegister?"Criar conta":"Iniciar sessão");submit.type="submit";f.append(submit);
    f.onsubmit=async e=>{
      e.preventDefault();submit.disabled=true;
      try{
        await api(isRegister?"/auth/register":"/auth/login","POST",isRegister?{name:name.value,email:email.value,password:password.value}:{email:email.value,password:password.value});
        await boot();
      }catch(ex){error(ex);}finally{submit.disabled=false;}
    };
    box.append(f,button(isRegister?"Já tenho conta":"Criar uma conta",()=>{isRegister=!isRegister;form()},"switch"));
  }form();
}
async function loadWorkspaces(){
  state.workspaces=(await api("/workspaces")).workspaces;
  if(!state.workspaces.some(w=>w.id===state.workspace))state.workspace=state.workspaces[0]?.id||null;
  await loadProjects();
}
async function loadProjects(){state.projects=state.workspace?(await api("/projects?workspaceId="+state.workspace)).projects:[];}
async function refresh(){await loadWorkspaces();render();}
function navigate(page){state.page=page;state.project=null;render();}
function render(){
 clear();root.replaceChildren();
 const app=$("div","app"),brand=$("div","brand");brand.append($("em","","N"),$("span","","Nord Workspace"));
 const top=$("div","top"),searchWrap=$("div","top-search"),search=input("Pesquisar projetos…"),actions=$("div","top-actions");
 search.className="search";search.setAttribute("aria-label","Pesquisar projetos");
 searchWrap.append(glyph("search"),search);
 search.oninput=()=>{if(state.page!=="projects"||state.project){state.page="projects";state.project=null;drawProjects(search.value);}else drawProjects(search.value);};
 actions.append($("span","user-name",state.me.name),button("Sair",async()=>{await api("/auth/logout","POST",{});state.me=null;auth();},"btn ghost top-logout"));
 top.append(searchWrap,actions);
 const rail=$("div","rail");
 rail.setAttribute("aria-label","Workspaces");
 for(const w of state.workspaces){
   const b=button((w.icon||w.name.charAt(0)).toUpperCase(),async()=>{state.workspace=w.id;state.project=null;state.page="dashboard";await refresh();},"workspace-icon"+(state.workspace===w.id?" active":""));
   b.title=w.name;b.setAttribute("aria-label","Abrir workspace "+w.name);rail.append(b);
 }
 const add=button("",()=>dialog("Novo workspace",[{key:"name",label:"Nome do workspace"}],async v=>{const result=await api("/workspaces","POST",v);state.workspace=result.id;state.page="dashboard";}),"create");add.append(glyph("plus"));add.title="Novo workspace";add.setAttribute("aria-label","Novo workspace");rail.append(add);
 const nav=$("nav","nav"),workspaceName=state.workspaces.find(w=>w.id===state.workspace)?.name||"O meu espaço";
 nav.append($("div","nav-workspace-name",workspaceName),$("h3","","O teu espaço"));
 for(const [label,page,iconName] of [["Dashboard","dashboard","grid"],["Projetos","projects","folder"],["Tarefas","tasks","tasks"],["Recentes","recent","clock"],["Equipa","team","users"]]){
   nav.append(withIcon(label,iconName,()=>navigate(page),"nav-link"+(state.page===page&&!state.project?" active":"")));
 }
 if(state.me.role==="PLATFORM_OWNER"){
   nav.append($("h3","nav-admin-title","Plataforma"),withIcon("Administração","settings",()=>navigate("admin"),"nav-link"+(state.page==="admin"&&!state.project?" active":"")));
 }
 const footer=$("div","bottom");footer.append($("div","footer-avatar",state.me.name.slice(0,1).toUpperCase()));
 const text=$("div","footer-meta");text.append($("strong","",state.me.name),$("small","",workspaceName));footer.append(text);nav.append(footer);
 const main=$("main","main");main.id="main";app.append(brand,top,rail,nav,main);root.append(app);
 if(state.project){drawProject();return;}
 if(state.page==="dashboard")drawDashboard();
 else if(state.page==="projects"||state.page==="recent")drawProjects();
 else if(state.page==="tasks")drawGlobalTasks();
 else if(state.page==="team")drawTeam();
 else if(state.page==="admin")drawAdmin();
}
function heading(title,action){const h=$("div","pagehead");h.append($("h1","",title));if(action)h.append(action);return h}
function folder(project){
 const b=button("",async()=>{state.project=project.id;state.tab="tasks";render();},"folder");
 const visual=$("span","folder-visual");visual.append(glyph("folder"));b.append(visual,$("span","name",project.name));
 if(project.description)b.append($("span","folder-desc",project.description));
 return b;
}
function drawProjects(filter=""){
 const main=document.getElementById("main");if(!main)return;main.replaceChildren();
 const h=$("div","pagehead");const copy=$("div");copy.append($("div","eyebrow","EXPLORADOR DE PROJETOS"),$("h1","","Os teus projetos"),$("p","muted","Cada pasta abre o seu espaço com tarefas, Git, documentos e equipa."));
 h.append(copy,withIcon("Novo projeto","plus",projectDialog));main.append(h);
 const projects=state.projects.filter(p=>p.name.toLowerCase().includes(filter.toLowerCase()));
 if(!projects.length){main.append(emptyState("folder",filter?"Nenhum resultado":"Ainda não tens projetos",filter?"Experimenta outro nome na pesquisa.":"Cria a primeira pasta para começar a organizar o teu trabalho.",filter?null:"Criar projeto",filter?null:projectDialog));return;}
 const grid=$("div","folders project-grid");for(const p of projects)grid.append(folder(p));main.append(grid);
}
async function drawDashboard(){
 const main=document.getElementById("main");
 const workspaceId=state.workspace;
 const name=state.workspaces.find(w=>w.id===workspaceId)?.name||"O meu espaço";
 const hero=$("section","hero dashboard-hero");
 const copy=$("div","hero-copy");
 const hours=new Date().getHours(),greeting=hours<12?"Bom dia":hours<19?"Boa tarde":"Boa noite";
 copy.append($("div","eyebrow","WORKSPACE / "+name.toUpperCase()),$("h1","",greeting+", "+state.me.name.split(" ")[0]+" 👋"),$("p","muted","Os teus projetos, prioridades e atividade num só lugar."));
 const quick=$("div","hero-actions");quick.append(withIcon("Novo projeto","plus",projectDialog),withIcon("Nova tarefa","tasks",chooseTaskProject,"btn ghost"));
 hero.append(copy,quick);main.append(hero);
 const metrics=$("div","cards dashboard-metrics");
 const items=[["folder","Projetos",state.projects.length],["tasks","Tarefas ativas","…"],["check","Concluídas","…"],["users","Workspaces",state.workspaces.length]];
 const valueNodes=[];
 for(const [iconName,label,value] of items){
   const card=$("div","stat"),mark=$("div","stat-mark");mark.append(glyph(iconName));
   const txt=$("div","stat-text"),num=$("b","",String(value));txt.append(num,$("span","muted",label));valueNodes.push(num);
   card.append(mark,txt);metrics.append(card);
 }main.append(metrics);
 const projects=$("section","dashboard-projects"),ph=$("div","section-head");
 const heading=$("div");heading.append($("div","eyebrow","ACESSO RÁPIDO"),$("h2","","Projetos recentes"));
 ph.append(heading,button("Ver todos →",()=>navigate("projects"),"text-action"));projects.append(ph);
 if(!state.projects.length){
   projects.append(emptyState("folder","O teu primeiro projeto começa aqui","Cada pasta terá as suas tarefas, ligações Git, documentos e, se precisares, um chat de equipa.","Criar primeiro projeto",projectDialog));
 }else{
   const grid=$("div","folders dashboard-folder-grid");
   for(const p of state.projects.slice(0,8))grid.append(folder(p));
   projects.append(grid);
 }
 main.append(projects);
 const two=$("div","dashboard-bottom"),tasks=$("section","panel dashboard-panel"),activity=$("section","panel dashboard-panel");
 const taskHead=$("div","panel-heading");taskHead.append($("h2","","As minhas tarefas"),button("Ver todas →",()=>navigate("tasks"),"text-action"));tasks.append(taskHead);
 const actHead=$("div","panel-heading");actHead.append($("h2","","Atividade recente"),$("span","muted small","Por projeto"));activity.append(actHead);
 two.append(tasks,activity);main.append(two);
 const all=[];
 await Promise.all(state.projects.map(async p=>{
   try{const result=await api("/projects/"+p.id+"/tasks");for(const t of result.tasks)all.push({task:t,project:p});}
   catch(ex){error(ex);}
 }));
 if(state.workspace!==workspaceId||state.page!=="dashboard"||state.project)return;
 const active=all.filter(v=>v.task.status!=="DONE");
 const done=all.length-active.length;
 valueNodes[1].textContent=String(active.length);valueNodes[2].textContent=String(done);
 active.sort((a,b)=>(a.task.dueDate||"9999").localeCompare(b.task.dueDate||"9999")||(a.task.priority==="HIGH"?-1:1));
 if(!active.length)tasks.append(emptyState("check","Tudo em ordem","Não há tarefas pendentes neste workspace.",all.length?null:"Criar tarefa",all.length?null:chooseTaskProject));
 else for(const {task,project} of active.slice(0,5))tasks.append(taskRow(task,project));
 const recent=all.filter(v=>v.task.createdAt).sort((a,b)=>String(b.task.createdAt).localeCompare(String(a.task.createdAt))).slice(0,5);
 if(!recent.length){
   activity.append(emptyState("activity","Ainda sem atividade","A criação de tarefas aparecerá aqui assim que começares a trabalhar."));
 }else for(const {task,project} of recent){
   const line=$("div","activity-row"),mark=$("span","activity-mark");mark.append(glyph(task.status==="DONE"?"check":"tasks"));
   const detail=$("div","activity-detail");detail.append($("strong","",task.status==="DONE"?"Tarefa concluída":"Tarefa criada"),$("span","",task.title+" · "+project.name));
   line.append(mark,detail);activity.append(line);
 }
}
async function drawProject(){
  if(state.timer){clearInterval(state.timer);state.timer=null;}
  const main=document.getElementById("main");
  main.replaceChildren($("p","muted","A carregar projeto…"));
  try{
    const project=await api("/projects/"+state.project);
    const members=(await api("/projects/"+state.project+"/members")).members;
    if(state.project!==project.id)return;
    main.replaceChildren();
    main.append(button("← Voltar aos projetos",()=>{state.project=null;state.page="projects";render()},"btn ghost"));
    const hero=$("div","hero section"),meta=$("div","projectmeta");
    meta.append($("span","ico","📁"));
    const desc=$("div");desc.append($("h1","",project.name),$("p","muted",project.description||"Sem descrição"));
    meta.append(desc);hero.append(meta,$("p","muted small",members.length+" membro(s) no projeto"));main.append(hero);
    const layout=$("div","projectlayout"),content=$("div","projectcontent"),tabs=$("div","tabs");
    for(const [key,label] of [["tasks","☑ Tarefas"],["git","◇ Git / Repositório"],["documents","▤ Documentos"],["team","♙ Equipa"]]){
      tabs.append(button(label,()=>{state.tab=key;drawProject()},state.tab===key?"active":""));
    }content.append(tabs);
    const panel=$("div","panel");content.append(panel);layout.append(content);
    if(members.length>1){
      const chat=$("aside","chat");chat.append($("div","chathead","Chat · "+project.name+"  •  "+members.length+" membros"));
      const log=$("div","chatlog"),form=$("form"),entry=input("Enviar mensagem…"),send=$("button","btn","➤");send.type="submit";
      form.append(entry,send);chat.append(log,form);layout.append(chat);
      const updateChat=async()=>{
        const r=await api("/projects/"+project.id+"/messages");
        if(state.project!==project.id)return;
        log.replaceChildren();
        for(const m of r.messages.slice().reverse()){const box=$("div","msg");box.append($("b","",m.author),$("p","",m.body));log.append(box);}
        log.scrollTop=log.scrollHeight;
      };
      form.onsubmit=async e=>{e.preventDefault();if(!entry.value.trim())return;try{await api("/projects/"+project.id+"/messages","POST",{body:entry.value});entry.value="";await updateChat();}catch(ex){error(ex);}};
      await updateChat();state.timer=setInterval(()=>updateChat().catch(()=>{}),5000);
    }
    main.append(layout);
    if(state.tab==="tasks")await projectTasks(panel,project);
    else if(state.tab==="git")projectGit(panel,project);
    else if(state.tab==="documents")await projectDocs(panel,project);
    else if(state.tab==="team")projectTeam(panel,project,members);
  }catch(ex){main.replaceChildren(msg(ex));}
}
async function projectTasks(panel,project){
  panel.append(heading("Tarefas",button("+ Nova tarefa",()=>dialog("Nova tarefa",[{key:"title",label:"Título da tarefa"},{key:"dueDate",label:"Data (AAAA-MM-DD)",optional:true}],v=>api("/projects/"+project.id+"/tasks","POST",v)))));
  const tasks=(await api("/projects/"+project.id+"/tasks")).tasks;
  for(const t of tasks){
    const line=$("div","line"),label=$("label"),checkbox=input("","checkbox");
    checkbox.checked=t.status==="DONE";
    checkbox.onchange=async()=>{try{await api("/tasks/"+t.id,"PATCH",{status:checkbox.checked?"DONE":"TODO"});await drawProject();}catch(ex){error(ex);}};
    label.append(checkbox,$("span","",t.title));line.append(label,$("span","tag",t.priority+" · "+t.status));panel.append(line);
  }if(!tasks.length)panel.append($("p","empty","Ainda não existem tarefas neste projeto."));
}
function projectGit(panel,project){
  panel.append($("h2","","Repositório Git"),$("p","muted","Associa o repositório do projeto para o abrir rapidamente."));
  if(project.gitUrl){const a=$("a","",project.gitUrl);a.href=project.gitUrl;a.target="_blank";a.rel="noopener noreferrer";panel.append(a);}
  panel.append($("div","section"),button("Editar ligação",()=>dialog("Ligar Git",[{key:"url",label:"https://github.com/organização/repositório",optional:true}],v=>api("/projects/"+project.id+"/git","PATCH",v))));
}
async function projectDocs(panel,project){
  panel.append(heading("Documentos",button("+ Adicionar ligação",()=>dialog("Novo documento",[{key:"title",label:"Nome do documento"},{key:"url",label:"URL HTTPS"}],v=>api("/projects/"+project.id+"/documents","POST",v)))));
  const docs=(await api("/projects/"+project.id+"/documents")).documents;
  for(const d of docs){const line=$("div","line"),a=$("a","",d.title);a.href=d.url;a.target="_blank";a.rel="noopener noreferrer";line.append(a);panel.append(line);}
  if(!docs.length)panel.append($("p","empty","Nenhum documento associado."));
}
function projectTeam(panel,project,members){
  panel.append(heading("Equipa",button("+ Adicionar membro",()=>dialog("Adicionar membro ao projeto",[{key:"email",label:"Email do membro"}],v=>api("/projects/"+project.id+"/members","POST",v)))));
  for(const m of members)panel.append($("div","line",m.name));
  panel.append($("p","muted small","O membro já tem de pertencer ao workspace."));
}
async function drawGlobalTasks(){
  const main=document.getElementById("main");main.append(heading("Tarefas globais"));
  for(const p of state.projects){
    try{
      for(const t of (await api("/projects/"+p.id+"/tasks")).tasks){
        const line=$("div","line");line.append($("span","",t.title),$("span","tag",p.name+" · "+t.status));main.append(line);
      }
    }catch(ex){}
  }
}
async function drawTeam(){
  const main=document.getElementById("main");main.append(heading("Equipa",button("+ Convidar utilizador",()=>dialog("Convidar para o workspace",[{key:"email",label:"Email do utilizador"}],v=>api("/workspaces/"+state.workspace+"/members","POST",v)))));
  try{
    const r=await api("/workspaces/"+state.workspace+"/members");
    for(const m of r.members){const line=$("div","line");line.append($("span","",m.name),$("span","tag",m.role));main.append(line);}
  }catch(ex){error(ex);}
}
async function drawAdmin(){
  const main=document.getElementById("main");main.append(heading("Administração · Platform Owner"));
  try{
    const stats=await api("/admin/stats");const cards=$("div","cards");
    for(const [key,label] of [["users","Utilizadores"],["workspaces","Workspaces"],["projects","Projetos"],["tasks","Tarefas"]]){
      const card=$("div","stat");card.append($("b","",String(stats[key])),$("span","muted",label));cards.append(card);
    }main.append(cards);
    const users=(await api("/admin/users")).users;
    const panel=$("div","panel section");panel.append($("h2","","Utilizadores"));
    for(const u of users){const row=$("div","line");row.append($("span","",u.name+" · "+u.email),$("span","tag",u.role));panel.append(row);}
    main.append(panel);
  }catch(ex){main.append(msg(ex));}
}
boot();
