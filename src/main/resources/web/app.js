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
  const top=$("div","top"),search=input("⌕  Pesquisar projetos…"),topActions=$("div","row");
  search.className="search";search.oninput=()=>{if(state.page==="projects")drawProjects(search.value);};
  topActions.append($("span","muted small",state.me.name),button("Sair",async()=>{await api("/auth/logout","POST",{});state.me=null;auth();},"btn ghost"));
  top.append(search,topActions);
  const rail=$("div","rail");
  for(const w of state.workspaces){
    const b=button((w.icon||w.name.charAt(0)).toUpperCase(),async()=>{state.workspace=w.id;state.project=null;state.page="dashboard";await refresh();},""+(state.workspace===w.id?"active":""));
    b.title=w.name;rail.append(b);
  }
  rail.append(button("+",()=>dialog("Novo workspace",[{key:"name",label:"Nome do workspace"}],async v=>{const result=await api("/workspaces","POST",v);state.workspace=result.id;state.page="dashboard";}),"create"));
  const nav=$("nav","nav");nav.append($("h3","","Navegação"));
  for(const [label,page] of [["⌂  Dashboard","dashboard"],["▱  Projetos","projects"],["☑  Tarefas","tasks"],["⌕  Recentes","recent"],["♙  Equipa","team"]]){
    nav.append(button(label,()=>navigate(page),state.page===page?"active":""));
  }
  if(state.me.role==="PLATFORM_OWNER")nav.append(button("⚙  Administração",()=>navigate("admin"),state.page==="admin"?"active":""));
  nav.append($("div","bottom",state.workspaces.find(w=>w.id===state.workspace)?.name||"Seleciona um espaço"));
  const main=$("main","main");main.id="main";app.append(brand,top,rail,nav,main);root.append(app);
  if(state.project){drawProject();return;}
  if(state.page==="dashboard")drawDashboard();
  else if(state.page==="projects")drawProjects();
  else if(state.page==="tasks")drawGlobalTasks();
  else if(state.page==="team")drawTeam();
  else if(state.page==="admin")drawAdmin();
  else if(state.page==="recent")drawProjects();
}
function heading(title,action){const h=$("div","pagehead");h.append($("h1","",title));if(action)h.append(action);return h}
function folder(project){
  const b=button("",async()=>{state.project=project.id;state.tab="tasks";render();},"folder");
  b.append($("span","ico","📁"),$("span","name",project.name));
  return b;
}
function drawProjects(filter=""){
  const main=document.getElementById("main");if(!main)return;main.replaceChildren();
  main.append(heading("Projetos",button("+ Novo projeto",()=>dialog("Novo projeto",[{key:"name",label:"Nome do projeto"},{key:"description",label:"Descrição",optional:true}],v=>api("/projects?workspaceId="+state.workspace,"POST",v)))));
  main.append($("p","muted","Cada pasta é um projeto. Abre uma pasta para ver tarefas, Git, documentos e equipa."));
  const grid=$("div","folders");for(const p of state.projects.filter(p=>p.name.toLowerCase().includes(filter.toLowerCase())))grid.append(folder(p));
  if(!grid.children.length)grid.append($("p","empty","Ainda não existem projetos aqui."));main.append(grid);
}
async function drawDashboard(){
  const main=document.getElementById("main");
  const name=state.workspaces.find(w=>w.id===state.workspace)?.name||"Workspace";
  const hero=$("div","hero");hero.append($("h1","","Bom dia, "+state.me.name+" 👋"),$("p","muted","O teu espaço: "+name));main.append(hero);
  const cards=$("div","cards");
  for(const [v,label] of [[state.projects.length,"Projetos"],["—","Tarefas ativas"],["—","Concluídas"],[state.workspaces.length,"Workspaces"]]){
    const card=$("div","stat");card.append($("b","",String(v)),$("span","muted",label));cards.append(card);
  }main.append(cards);
  const sec=$("section","section"),head=heading("Projetos recentes",button("Ver todos",()=>navigate("projects"),"btn ghost"));sec.append(head);
  const grid=$("div","folders");for(const p of state.projects.slice(0,10))grid.append(folder(p));sec.append(grid);main.append(sec);
  const section=$("section","section grid2"),tasks=$("div","panel"),activity=$("div","panel");
  tasks.append($("h2","","Próximas tarefas"));activity.append($("h2","","Atividade do workspace"));
  section.append(tasks,activity);main.append(section);
  let active=0,done=0;
  for(const p of state.projects.slice(0,30)){
    try{
      const t=(await api("/projects/"+p.id+"/tasks")).tasks;
      active+=t.filter(x=>x.status!=="DONE").length;done+=t.filter(x=>x.status==="DONE").length;
      for(const item of t.filter(x=>x.status!=="DONE").slice(0,2)){
        const r=$("div","line");r.append($("span","",item.title),$("span","tag",p.name));tasks.append(r);
      }
    }catch(ex){}
  }
  cards.children[1].firstChild.textContent=String(active);
  cards.children[2].firstChild.textContent=String(done);
  activity.append($("p","muted","Abre um projeto para consultar mensagens e atualizações da tua equipa."));
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
  const main=document.getElementById("main");main.append(heading("Equipa",button("+ Convidar utilizador",()=>dialog("Convidar para o workspace",[{key:"email",label:"Email do utilizador"}],v=>api("/workspaces/"+state.workspace+"/members","POST",v))));
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
