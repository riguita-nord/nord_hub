package com.nordlab.hub.api;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ApiIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;

    @Test void accountCanPersistWorkspaceDataButCannotReadAnotherWorkspace() throws Exception {
        JsonNode first=register("ricardo@nord.test");
        String token=first.path("accessToken").asText();
        String firstWorkspace=first.path("workspace").path("id").asText();

        MvcResult projectResult=mvc.perform(post("/api/v1/workspaces/{id}/projects",firstWorkspace)
                .header("Authorization","Bearer "+token).contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(Map.of("name","Nord Hub","status","Em Foco","priority","Alta","progress",12))))
            .andExpect(status().isCreated()).andExpect(jsonPath("$.payload.name").value("Nord Hub")).andReturn();
        String projectId=mapper.readTree(projectResult.getResponse().getContentAsString()).path("id").asText();

        mvc.perform(post("/api/v1/workspaces/{id}/tasks",firstWorkspace)
                .header("Authorization","Bearer "+token).contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(Map.of("title","Persist task","projectId",projectId,"status","Por Fazer","priority","Alta"))))
            .andExpect(status().isCreated()).andExpect(jsonPath("$.payload.title").value("Persist task"));

        JsonNode second=register("other@nord.test");
        mvc.perform(get("/api/v1/workspaces/{id}/projects",firstWorkspace)
                .header("Authorization","Bearer "+second.path("accessToken").asText()))
            .andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/workspaces/{id}/projects",firstWorkspace))
            .andExpect(status().isUnauthorized());
    }

    @Test void rejectsTasksThatReferenceProjectsOutsideTheWorkspace() throws Exception {
        JsonNode first=register("owner@nord.test");
        String token=first.path("accessToken").asText();
        String workspace=first.path("workspace").path("id").asText();
        mvc.perform(post("/api/v1/workspaces/{id}/tasks",workspace)
                .header("Authorization","Bearer "+token).contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(Map.of("title","Bad link","projectId","00000000-0000-0000-0000-000000000001"))))
            .andExpect(status().isBadRequest());
    }

    @Test void workspaceOwnerCanInviteOnlyTheMatchingEmailOnce() throws Exception {
        JsonNode owner=register("owner-invite@nord.test");
        String ownerToken=owner.path("accessToken").asText();
        String workspace=owner.path("workspace").path("id").asText();
        MvcResult inviteResult=mvc.perform(post("/api/v1/workspaces/{id}/invitations",workspace)
                .header("Authorization","Bearer "+ownerToken).contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(Map.of("email","invitee@nord.test","role","EDITOR"))))
            .andExpect(status().isCreated()).andExpect(jsonPath("$.role").value("EDITOR")).andReturn();
        String inviteToken=mapper.readTree(inviteResult.getResponse().getContentAsString()).path("token").asText();

        JsonNode invitee=register("invitee@nord.test");
        String inviteeToken=invitee.path("accessToken").asText();
        mvc.perform(post("/api/v1/invitations/accept")
                .header("Authorization","Bearer "+inviteeToken).contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(Map.of("token",inviteToken))))
            .andExpect(status().isOk()).andExpect(jsonPath("$.id").value(workspace)).andReturn();
        mvc.perform(get("/api/v1/workspaces/{id}/projects",workspace)
                .header("Authorization","Bearer "+inviteeToken))
            .andExpect(status().isOk());
        mvc.perform(post("/api/v1/invitations/accept")
                .header("Authorization","Bearer "+inviteeToken).contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(Map.of("token",inviteToken))))
            .andExpect(status().isGone());
    }

    private JsonNode register(String email) throws Exception {
        MvcResult result=mvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(Map.of("email",email,"password","Strong-passphrase-2026","displayName","Test User","workspaceName","Test space"))))
            .andExpect(status().isCreated()).andReturn();
        return mapper.readTree(result.getResponse().getContentAsString());
    }
}
