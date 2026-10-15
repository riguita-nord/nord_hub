package com.nordlab.hub.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nordlab.hub.domain.WorkspaceData;
import com.nordlab.hub.repository.WorkspaceDataRepository;
import com.nordlab.hub.service.CurrentUserService;
import com.nordlab.hub.service.WorkspaceAccessService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestClient;
import org.springframework.web.server.ResponseStatusException;

@RestController
public class GithubIntegrationController {
    private static final String RECORD_TYPE="GITHUB_REPOSITORY";
    private final WorkspaceDataRepository records; private final WorkspaceAccessService access; private final CurrentUserService current;
    private final JdbcTemplate jdbc; private final ObjectMapper mapper; private final RestClient github;
    private final String token; private final byte[] webhookSecret; private final boolean aiConfigured;

    public GithubIntegrationController(WorkspaceDataRepository records,WorkspaceAccessService access,CurrentUserService current,
            JdbcTemplate jdbc,ObjectMapper mapper,RestClient.Builder builder,
            @Value("${nordhub.github.token:}") String token,
            @Value("${nordhub.github.api-url:https://api.github.com}") String apiUrl,
            @Value("${nordhub.github.webhook-secret:}") String webhookSecret,
            @Value("${nordhub.ai.api-key:}") String aiKey) {
        this.records=records;this.access=access;this.current=current;this.jdbc=jdbc;this.mapper=mapper;
        this.token=token;this.webhookSecret=webhookSecret.getBytes(StandardCharsets.UTF_8);this.aiConfigured=!aiKey.isBlank();
        RestClient.Builder configured=builder.baseUrl(apiUrl);
        if(!token.isBlank())configured.defaultHeader(HttpHeaders.AUTHORIZATION,"Bearer "+token);
        configured.defaultHeader(HttpHeaders.ACCEPT,"application/vnd.github+json").defaultHeader("X-GitHub-Api-Version","2022-11-28");
        this.github=configured.build();
    }

    @GetMapping("/api/v1/integrations/status")
    public IntegrationStatus status(){return new IntegrationStatus(!token.isBlank(),webhookSecret.length>=32,aiConfigured,"NOT_CONFIGURED");}


    @GetMapping("/api/v1/workspaces/{workspaceId}/github/repositories")
    public List<LinkedRepository> list(@PathVariable UUID workspaceId){access.require(workspaceId,current.id(),false);return records.findAllByWorkspaceIdAndRecordTypeOrderByUpdatedAtDesc(workspaceId,RECORD_TYPE).stream().map(this::view).toList();}

    @PostMapping("/api/v1/workspaces/{workspaceId}/github/repositories")
    @Transactional
    public LinkedRepository link(@PathVariable UUID workspaceId,@Valid @RequestBody LinkRequest request){
        UUID actor=current.id();access.require(workspaceId,actor,true);requireToken();String fullName=request.fullName().trim();
        if(request.projectId()!=null&&!records.existsByIdAndWorkspaceIdAndRecordType(request.projectId(),workspaceId,"PROJECT"))throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"projectId must belong to this workspace");
        JsonNode remote=github.get().uri("/repos/"+fullName).retrieve().body(JsonNode.class);
        if(remote==null||!remote.path("full_name").asText().equalsIgnoreCase(fullName))throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,"GitHub did not verify the repository");
        if(records.findAllByWorkspaceIdAndRecordTypeOrderByUpdatedAtDesc(workspaceId,RECORD_TYPE).stream().anyMatch(r->fullName.equalsIgnoreCase(String.valueOf(r.getPayload().get("fullName")))))throw new ResponseStatusException(HttpStatus.CONFLICT,"Repository is already linked");
        Map<String,Object> payload=Map.of("fullName",remote.path("full_name").asText(),"private",remote.path("private").asBoolean(),"url",remote.path("html_url").asText(),"defaultBranch",remote.path("default_branch").asText(),"projectId",request.projectId()==null?"":request.projectId().toString());
        return view(records.save(new WorkspaceData(UUID.randomUUID(),workspaceId,RECORD_TYPE,payload,actor)));
    }

    @GetMapping("/api/v1/workspaces/{workspaceId}/github/repositories/{repositoryId}/activity")
    public RepositoryActivity activity(@PathVariable UUID workspaceId,@PathVariable UUID repositoryId){
        access.require(workspaceId,current.id(),false);WorkspaceData linked=find(workspaceId,repositoryId);requireToken();String fullName=String.valueOf(linked.getPayload().get("fullName"));
        JsonNode commits=github.get().uri("/repos/"+fullName+"/commits?per_page=20").retrieve().body(JsonNode.class);
        JsonNode pulls=github.get().uri("/repos/"+fullName+"/pulls?state=all&per_page=20").retrieve().body(JsonNode.class);
        return new RepositoryActivity(fullName,items(commits,true),items(pulls,false));
    }

    @DeleteMapping("/api/v1/workspaces/{workspaceId}/github/repositories/{repositoryId}")
    @Transactional
    public void unlink(@PathVariable UUID workspaceId,@PathVariable UUID repositoryId){access.require(workspaceId,current.id(),true);records.delete(find(workspaceId,repositoryId));}

    @PostMapping("/api/v1/integrations/github/webhook")
    @Transactional
    public Map<String,Object> webhook(@RequestBody byte[] body,@RequestHeader(value="X-Hub-Signature-256",required=false) String signature,
            @RequestHeader(value="X-GitHub-Delivery",required=false) String delivery,@RequestHeader(value="X-GitHub-Event",required=false) String event){
        if(webhookSecret.length<32)throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,"GitHub webhooks are not configured");
        if(delivery==null||delivery.isBlank()||event==null||event.isBlank()||!validSignature(body,signature))throw new ResponseStatusException(HttpStatus.UNAUTHORIZED,"GitHub webhook signature is invalid");
        try{JsonNode payload=mapper.readTree(body);String repo=payload.path("repository").path("full_name").asText("");int inserted=jdbc.update("INSERT INTO github_deliveries(delivery_id,event_type,repository_full_name,payload) VALUES (?,?,?,?::jsonb) ON CONFLICT (delivery_id) DO NOTHING",delivery,event,repo,mapper.writeValueAsString(payload));if(inserted==1)createTaskNotifications(repo,event,payload);return Map.of("accepted",inserted==1,"duplicate",inserted==0);}catch(ResponseStatusException e){throw e;}catch(Exception e){throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Webhook payload is invalid");}
    }

    private void createTaskNotifications(String fullName,String event,JsonNode payload){
        if(fullName.isBlank())return;List<WorkspaceData> linked=records.findAllByRecordType(RECORD_TYPE).stream().filter(r->fullName.equalsIgnoreCase(String.valueOf(r.getPayload().get("fullName")))).toList();
        if("push".equals(event)){JsonNode commits=payload.path("commits");if(commits.isArray())for(JsonNode commit:commits){String message=commit.path("message").asText("");notifyReferencedTasks(linked,fullName,"Commit GitHub associado",message,taskReferences(message));}}
        if("pull_request".equals(event)){String action=payload.path("action").asText("");if(!List.of("opened","reopened","ready_for_review","closed").contains(action))return;JsonNode pr=payload.path("pull_request");String title=pr.path("title").asText("Pull request");String details=title+" #"+pr.path("number").asText("");List<String> refs=taskReferences(title+" "+pr.path("body").asText(""));notifyReferencedTasks(linked,fullName,"Pull request GitHub",details,refs);}
    }
    private void notifyReferencedTasks(List<WorkspaceData> linked,String repository,String title,String details,List<String> refs){
        for(WorkspaceData link:linked)for(String reference:refs){try{UUID taskId=UUID.fromString(reference);if(!records.existsByIdAndWorkspaceIdAndRecordType(taskId,link.getWorkspaceId(),"TASK"))continue;Map<String,Object> notice=Map.of("title",title,"body",repository+" · "+details+" · tarefa "+taskId,"kind","GitHub","time",java.time.Instant.now().toString());records.save(new WorkspaceData(UUID.randomUUID(),link.getWorkspaceId(),"NOTIFICATION",notice,link.getCreatedBy()));}catch(IllegalArgumentException ignored){/* Ignore malformed task references. */}}
    }

    private boolean validSignature(byte[] body,String header){try{if(header==null||!header.startsWith("sha256="))return false;Mac mac=Mac.getInstance("HmacSHA256");mac.init(new SecretKeySpec(webhookSecret,"HmacSHA256"));String expected="sha256="+HexFormat.of().formatHex(mac.doFinal(body));return MessageDigest.isEqual(expected.getBytes(StandardCharsets.US_ASCII),header.getBytes(StandardCharsets.US_ASCII));}catch(Exception e){return false;}}
    private List<ActivityItem> items(JsonNode list,boolean commits){List<ActivityItem> out=new ArrayList<>();if(list==null||!list.isArray())return out;for(JsonNode n:list){String title=commits?n.path("commit").path("message").asText(""):n.path("title").asText("");String referenceText=commits?title:title+" "+n.path("body").asText("");String url=n.path("html_url").asText("");String id=commits?n.path("sha").asText(""):Integer.toString(n.path("number").asInt());String actor=commits?n.path("author").path("login").asText("unknown"):n.path("user").path("login").asText("unknown");out.add(new ActivityItem(id,title,url,actor,taskReferences(referenceText),commits?"commit":"pull_request"));}return out;}
    private List<String> taskReferences(String text){java.util.regex.Matcher m=java.util.regex.Pattern.compile("(?i)\\[nord-task:([a-f0-9-]{36})\\]").matcher(text);List<String> ids=new ArrayList<>();while(m.find())ids.add(m.group(1));return ids;}
    private WorkspaceData find(UUID workspaceId,UUID id){return records.findByIdAndWorkspaceIdAndRecordType(id,workspaceId,RECORD_TYPE).orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND,"Linked repository not found"));}
    private LinkedRepository view(WorkspaceData r){Map<String,Object> p=r.getPayload();return new LinkedRepository(r.getId(),String.valueOf(p.get("fullName")),Boolean.TRUE.equals(p.get("private")),String.valueOf(p.get("url")),String.valueOf(p.get("defaultBranch")),String.valueOf(p.getOrDefault("projectId","")));}
    private void requireToken(){if(token.isBlank())throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,"Configure NORD_HUB_GITHUB_TOKEN on the server first");}
    public record IntegrationStatus(boolean githubConfigured,boolean githubWebhooksConfigured,boolean aiConfigured,String nordSaasStatus){}
    public record RemoteRepository(String fullName,boolean privateRepository,String url,String defaultBranch){}
    public record LinkedRepository(UUID id,String fullName,boolean privateRepository,String url,String defaultBranch,String projectId){}
    public record ActivityItem(String id,String title,String url,String actor,List<String> taskReferences,String type){}
    public record RepositoryActivity(String fullName,List<ActivityItem> commits,List<ActivityItem> pullRequests){}
    public record LinkRequest(@NotBlank @Pattern(regexp="^[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+$") @Size(max=255) String fullName,UUID projectId){}
}
