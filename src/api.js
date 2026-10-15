/* Same-origin API client. A missing /api/v1/config keeps the static demo running. */
class NordHubApi {
  constructor(){this.base='/api/v1';this.token=sessionStorage.getItem('nordHub.accessToken')||'';this.workspaceId=sessionStorage.getItem('nordHub.workspaceId')||'';}
  async discover(){try{const response=await fetch(`${this.base}/config`,{headers:{Accept:'application/json'}});if(!response.ok)return false;const data=await response.json();return data.apiEnabled===true}catch{return false}}
  async request(path,{method='GET',body}={}){const headers={Accept:'application/json'};if(body!==undefined)headers['Content-Type']='application/json';if(this.token)headers.Authorization=`Bearer ${this.token}`;const response=await fetch(`${this.base}${path}`,{method,headers,body:body===undefined?undefined:JSON.stringify(body)});if(response.status===204)return null;const data=await response.json().catch(()=>({}));if(!response.ok){if(response.status===401){this.token='';sessionStorage.removeItem('nordHub.accessToken')}throw new Error(data.message||data.error||`Pedido falhou (${response.status})`)}return data}
  async login(payload){const result=await this.request('/auth/login',{method:'POST',body:payload});this.acceptSession(result);return result}
  async register(payload){const result=await this.request('/auth/register',{method:'POST',body:payload});this.acceptSession(result);return result}
  acceptSession(result){this.token=result.accessToken;this.workspaceId=result.workspace.id;sessionStorage.setItem('nordHub.accessToken',this.token);sessionStorage.setItem('nordHub.workspaceId',this.workspaceId)}
  async me(){return this.request('/auth/me')}
  async list(collection){return this.request(`/workspaces/${this.workspaceId}/${collection}`)}
  async members(){return this.request(`/workspaces/${this.workspaceId}/members`)}
  async changeMemberRole(userId,role){return this.request(`/workspaces/${this.workspaceId}/members/${userId}/role`,{method:'PUT',body:{role}})}
  async createInvite(email,role){return this.request(`/workspaces/${this.workspaceId}/invitations`,{method:'POST',body:{email,role}})}
  async acceptInvite(token){const result=await this.request('/invitations/accept',{method:'POST',body:{token}});this.workspaceId=result.id;sessionStorage.setItem('nordHub.workspaceId',result.id);return result}
  async gateway(path,options){return this.request(`/gateway/${path}`,options)}
  async create(collection,payload){return this.request(`/workspaces/${this.workspaceId}/${collection}`,{method:'POST',body:payload})}
  async patch(collection,id,payload){return this.request(`/workspaces/${this.workspaceId}/${collection}/${id}`,{method:'PATCH',body:payload})}
  async remove(collection,id){return this.request(`/workspaces/${this.workspaceId}/${collection}/${id}`,{method:'DELETE'})}
  logout(){this.token='';this.workspaceId='';sessionStorage.removeItem('nordHub.accessToken');sessionStorage.removeItem('nordHub.workspaceId')}
}
window.nordHubApi=new NordHubApi();
