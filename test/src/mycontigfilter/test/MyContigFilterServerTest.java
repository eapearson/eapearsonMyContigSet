package mycontigfilter.test;

import java.io.File;
import java.net.URL;
import java.util.Arrays;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;

import junit.framework.Assert;

import org.ini4j.Ini;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import mycontigfilter.FilterContigsParams;
import mycontigfilter.FilterContigsResults;
import mycontigfilter.MyContigFilterServer;
import us.kbase.auth.AuthToken;
import us.kbase.common.service.JsonServerSyslog;
import us.kbase.common.service.RpcContext;
import us.kbase.common.service.UObject;
import us.kbase.workspace.CreateWorkspaceParams;
import us.kbase.workspace.ObjectSaveData;
import us.kbase.workspace.ProvenanceAction;
import us.kbase.workspace.SaveObjectsParams;
import us.kbase.workspace.WorkspaceClient;
import us.kbase.workspace.WorkspaceIdentity;

public class MyContigFilterServerTest {
    private static AuthToken token = null;
    private static Map<String, String> config = null;
    private static WorkspaceClient wsClient = null;
    private static String wsName = null;
    private static MyContigFilterServer impl = null;
    
    @BeforeClass
    public static void init() throws Exception {
        token = new AuthToken(System.getenv("KB_AUTH_TOKEN"));
        String configFilePath = System.getenv("KB_DEPLOYMENT_CONFIG");
        File deploy = new File(configFilePath);
        Ini ini = new Ini(deploy);
        config = ini.get("MyContigFilter");
        wsClient = new WorkspaceClient(new URL(config.get("workspace-url")), token);
        wsClient.setAuthAllowedForHttp(true);
        // These lines are necessary because we don't want to start linux syslog bridge service
        JsonServerSyslog.setStaticUseSyslog(false);
        JsonServerSyslog.setStaticMlogFile(new File(config.get("scratch"), "test.log").getAbsolutePath());
        impl = new MyContigFilterServer();
    }
    
    private static String getWsName() throws Exception {
        if (wsName == null) {
            long suffix = System.currentTimeMillis();
            wsName = "test_MyContigFilter_" + suffix;
            wsClient.createWorkspace(new CreateWorkspaceParams().withWorkspace(wsName));
        }
        return wsName;
    }
    
    private static RpcContext getContext() {
        return new RpcContext().withProvenance(Arrays.asList(new ProvenanceAction()
            .withService("MyContigFilter").withMethod("please_never_use_it_in_production")
            .withMethodParams(new ArrayList<UObject>())));
    }
    
    @AfterClass
    public static void cleanup() {
        if (wsName != null) {
            try {
                wsClient.deleteWorkspace(new WorkspaceIdentity().withWorkspace(wsName));
                System.out.println("Test workspace was deleted");
            } catch (Exception ex) {
                ex.printStackTrace();
            }
        }
    }
    
    @Test
    public void testFilterContigsOk() throws Exception {
        String objName = "contigset.1";
        Map<String, Object> contig1 = new LinkedHashMap<String, Object>();
        contig1.put("id", "1");
        contig1.put("length", 10);
        contig1.put("md5", "md5");
        contig1.put("sequence", "agcttttcat");
        Map<String, Object> contig2 = new LinkedHashMap<String, Object>();
        contig2.put("id", "2");
        contig2.put("length", 5);
        contig2.put("md5", "md5");
        contig2.put("sequence", "agctt");
        Map<String, Object> contig3 = new LinkedHashMap<String, Object>();
        contig3.put("id", "3");
        contig3.put("length", 12);
        contig3.put("md5", "md5");
        contig3.put("sequence", "agcttttcatgg");
        Map<String, Object> obj = new LinkedHashMap<String, Object>();
        obj.put("contigs", Arrays.asList(contig1, contig2, contig3));
        obj.put("id", "id");
        obj.put("md5", "md5");
        obj.put("name", "name");
        obj.put("source", "source");
        obj.put("source_id", "source_id");
        obj.put("type", "type");
        wsClient.saveObjects(new SaveObjectsParams().withWorkspace(getWsName()).withObjects(Arrays.asList(
                new ObjectSaveData().withType("KBaseGenomes.ContigSet").withName(objName).withData(new UObject(obj)))));
        FilterContigsResults ret = impl.filterContigs(new FilterContigsParams().withWorkspace(getWsName())
                .withContigsetId(objName).withMinLength(10L), token, getContext());
        //Assert.assertEquals(1L, (long)ret.getContigCount());
        Assert.assertEquals(3L, (long)ret.getNInitialContigs());
        Assert.assertEquals(1L, (long)ret.getNContigsRemoved());
        Assert.assertEquals(2L, (long)ret.getNContigsRemaining());
        try {
            impl.filterContigs(new FilterContigsParams().withWorkspace(getWsName())
                .withContigsetId(objName), token, getContext());
            Assert.fail("Error is expected above");
        } catch (Exception ex) {
            Assert.assertEquals("Parameter min_length is not set in input arguments", ex.getMessage());
        }
        try {
            impl.filterContigs(new FilterContigsParams().withWorkspace(getWsName())
                .withContigsetId(objName).withMinLength(-10L), token, getContext());
            Assert.fail("Error is expected above");
        } catch (Exception ex) {
            Assert.assertEquals("min_length parameter shouldn't be negative (-10)", ex.getMessage());
        }
        try {
            impl.filterContigs(new FilterContigsParams().withWorkspace(getWsName())
                .withContigsetId("fake").withMinLength(10L), token, getContext());
            Assert.fail("Error is expected above");
        } catch (Exception ex) {
            Assert.assertEquals("Error loading original ContigSet object from workspace", ex.getMessage());
        }
    }
}
