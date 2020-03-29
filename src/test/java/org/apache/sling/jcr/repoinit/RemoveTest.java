/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.sling.jcr.repoinit;

import org.apache.jackrabbit.api.security.JackrabbitAccessControlList;
import org.apache.jackrabbit.api.security.user.Group;
import org.apache.jackrabbit.api.security.user.UserManager;
import org.apache.sling.jcr.repoinit.impl.AclUtil;
import org.apache.sling.jcr.repoinit.impl.TestUtil;
import org.apache.sling.jcr.repoinit.impl.UserUtil;
import org.apache.sling.repoinit.parser.RepoInitParsingException;
import org.apache.sling.testing.mock.sling.ResourceResolverType;
import org.apache.sling.testing.mock.sling.junit.SlingContext;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.jcr.Node;
import javax.jcr.RepositoryException;
import javax.jcr.Session;
import javax.jcr.security.AccessControlList;
import javax.jcr.security.AccessControlPolicy;

import java.security.Principal;
import java.util.UUID;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

public class RemoveTest {

    @Rule
    public final SlingContext context = new SlingContext(ResourceResolverType.JCR_OAK);

    private TestUtil U;
    private String path;
    private String userHomePath;
    private String groupId;
    private String groupPrincipalName;

    @Before
    public void before() throws RepositoryException, RepoInitParsingException {
        U = new TestUtil(context);
        U.parseAndExecute("create service user " + U.username);

        Node n = U.adminSession.getRootNode().addNode("tmp_" + U.id);
        path = n.getPath();

        UserManager uMgr = UserUtil.getUserManager(U.adminSession);
        Group gr = uMgr.createGroup("group_"+UUID.randomUUID().toString());
        groupId = gr.getID();
        groupPrincipalName = gr.getPrincipal().getName();

        U.adminSession.save();

        String setup = "set ACL for " + U.username + "\n"
                + "deny jcr:read on "+path+"\n"
                + "deny jcr:all on :repository\n"
                + "allow jcr:read on home(" + U.username + ")\n"
                + "end\n"
                + "\n"
                + "set ACL for " + groupPrincipalName + "\n"
                + "allow jcr:read on "+path+"\n"
                + "allow jcr:namespaceManagement on :repository\n"
                + "allow jcr:read on home(" + U.username + ")\n"
                + "end";
        U.parseAndExecute(setup);

        userHomePath = uMgr.getAuthorizable(U.username).getPath();
        assertPolicy(path, U.adminSession, 2);
        assertPolicy(null, U.adminSession, 2);
        assertPolicy(userHomePath, U.adminSession, 2);

    }

    @After
    public void after() throws RepositoryException, RepoInitParsingException {
        try {
            U.adminSession.removeItem(path);
            UserUtil.getUserManager(U.adminSession).getAuthorizable(groupId).remove();
            U.adminSession.save();
        } finally {
            U.cleanupUser();
        }
    }

    private static void assertPolicy(@Nullable String path, @NotNull Session session, int expectedSize) throws RepositoryException {
        AccessControlPolicy[] policies = session.getAccessControlManager().getPolicies(path);
        for (AccessControlPolicy policy : policies) {
            if (policy instanceof AccessControlList) {
                assertEquals(expectedSize, ((AccessControlList) policy).getAccessControlEntries().length);
                return;
            }
        }
        fail("No access control list found at " + path);
    }

    @Test
    public void testRemoveAllByPath() throws RepoInitParsingException, RepositoryException {
        String setup = "set ACL for " + U.username + "\n"
                + "remove * on "+path+",:repository, home("+U.username+")\n"
                + "end";
        U.parseAndExecute(setup);

        assertPolicy(path, U.adminSession, 1);
        assertPolicy(null, U.adminSession, 1);
        assertPolicy(userHomePath, U.adminSession, 1);
    }

    @Test
    public void testRemoveAllByPrincipal() throws RepoInitParsingException, RepositoryException {
        String setup = "set ACL on "+path+", home("+U.username+"), :repository\n"
                + "remove * for "+U.username+"\n" +
                "end";
        U.parseAndExecute(setup);

        assertPolicy(path, U.adminSession, 1);
        assertPolicy(null, U.adminSession, 1);
        assertPolicy(userHomePath, U.adminSession, 1);
    }

    @Test
    public void testRemoveAllByMultiplePrincipals() throws RepoInitParsingException, RepositoryException {
        String setup = "set ACL on "+path+"\n"
                + "remove * for "+U.username+", "+groupPrincipalName+"\n" +
                "end";
        U.parseAndExecute(setup);

        assertPolicy(path, U.adminSession, 0);
        assertPolicy(null, U.adminSession, 2);
        assertPolicy(userHomePath, U.adminSession, 2);
    }

    @Test(expected = RuntimeException.class)
    public void testRemoveByPath() throws RepoInitParsingException, RepositoryException {
        String setup = "set ACL for " + U.username + "\n"
                + "remove jcr:read on "+path+"\n"
                + "end";
        U.parseAndExecute(setup);
    }

    @Test(expected = RuntimeException.class)
    public void testRemoveByPath2() throws RepoInitParsingException, RepositoryException {
        String setup = "set ACL for " + groupPrincipalName + "\n"
                + "remove jcr:versionManagement on :repository\n"
                + "end";
        U.parseAndExecute(setup);
    }

    @Test(expected = RuntimeException.class)
    public void testRemoveByPrincipal() throws RepoInitParsingException, RepositoryException {
        String setup = "set ACL on home("+U.username+")\n"
                + "remove jcr:all for "+U.username+"\n" +
                "end";
        U.parseAndExecute(setup);
    }
}