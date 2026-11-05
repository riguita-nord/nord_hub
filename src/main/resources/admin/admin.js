"use strict";
const coreUrl="http://"+location.hostname+":8091/";
document.getElementById("coreLink").href=coreUrl;
document.getElementById("loginLink").href=coreUrl;
function el(tag,cls,text){const e=document.createElement(tag);if(cls)e.className=cls;if(text!==undefined)e.textContent=text;return e;}
async function get(path){const r=await fetch("/api/"+path,{credentials:"same-origin"});if(!r.ok)throw new Error(r.status===401?"not_owner":"request_failed");return r.json();}
async function start(){
 const login=document.getElementById("login"),stats=document.getElementById("stats"),people=document.getElementById("people");
 try{
  const me=await get("me");document.getElementById("identity").textContent=me.name+" · Platform Owner";
  const data=await get("stats");
  for(const [k,label] of [["users","Utilizadores"],["workspaces","Workspaces"],["projects","Projetos"],["tasks","Tarefas"]]){
   const box=el("div","card");box.append(el("span","",label),el("b","",String(data[k])));stats.append(box);
  }
  const users=(await get("users")).users;
  const target=document.getElementById("users");
  for(const user of users){
   const line=el("div","person");line.append(el("span","",user.name+" · "+user.email),el("span","pill",user.role));target.append(line);
  }
 }catch(error){
  stats.hidden=true;people.hidden=true;login.hidden=false;
  if(error.message!=="not_owner")document.getElementById("feedback").textContent="Não foi possível contactar o serviço Admin.";
 }
}
start();