package org.joget.marketplace;

import java.util.Map;
import java.util.StringTokenizer;
import java.util.stream.Collectors;
import java.io.IOException;
import java.io.PrintWriter;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;

import org.joget.apps.app.model.AppDefinition;
import org.joget.apps.app.model.UiHtmlInjectorPluginAbstract;
import org.joget.apps.app.service.AppDevUtil;
import org.joget.apps.app.service.AppUtil;
import org.joget.apps.app.service.DependenciesUtil;
import org.joget.commons.util.LogUtil;
import org.joget.commons.util.StringUtil;
import org.joget.plugin.base.PluginManager;
import org.joget.plugin.base.PluginWebSupport;
import org.json.JSONArray;
import org.json.JSONObject;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import javax.sql.DataSource;
import org.springframework.beans.BeansException;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Set;
import org.joget.apps.app.service.AppService;

public class UninstallPluginAlert extends UiHtmlInjectorPluginAbstract implements PluginWebSupport {
       
    @Override
    public String getName() {
        return "Uninstall Plugin Alert";
    }

    @Override
    public String getVersion() {
        return "9.1.0";
    }

    @Override
    public String getDescription() {
        return "UI Html Injector plugin to alert users the plugin is being used in which app before uninstalling.";
    }

    @Override
    public String[] getInjectUrlPatterns() {
        return new String[] {"/web/console/setting/plugin"};
    }
    
    @Override
    public String getHtml(HttpServletRequest request) {
        PluginManager pluginManager = (PluginManager) AppUtil.getApplicationContext().getBean("pluginManager");
        Map data = new HashMap();
        data.put("plugin", this);
        data.put("request", request);
        
        return pluginManager.getPluginFreeMarkerTemplate(data, getClassName(), "/templates/UninstallPluginAlertUiHtmlInjector.ftl", null);
    }

    @Override
    public boolean isIncludeForAjaxThemePageSwitching() {
        return false;
    }

    @Override
    public void webService(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        String body = request.getReader().lines().collect(Collectors.joining());
        JSONObject json = new JSONObject(body);
        JSONArray pluginClasses = json.getJSONArray("selectedList");
        PluginManager pluginManager = (PluginManager) AppUtil.getApplicationContext().getBean("pluginManager");
        
        Set<Map<String, String>> apps = new HashSet<>();
        Set<String> jarFiles = new HashSet<>();
        for (Object p: pluginClasses.toList()) {
            String jar = pluginManager.getJarFileName(p.toString());
            if (jar != null) {
                jarFiles.add(StringUtil.unescapeString(jar, StringUtil.TYPE_URL));
            }
            //LogUtil.info(getClassName(), "plugin class:" + p.toString());
            //get apps that use the plugin class by querying in several tables
            //apps = executeQery(p.toString(), apps);
            //LogUtil.info(getClassName(), apps.toString());
        }
        LogUtil.info(getClassName(), "Plugins stored in wflow/app_plugins are " + jarFiles.toString());

        // get appid and appname from published apps that use the plugin jars (get list of jar files frommsame logic that export app uses)
        // note that if there are jar files in app_src/<appid>/<app_id>_<app_version>/plugins, even if it is unused it will be detected here
        apps = getPublishedApps(jarFiles);
        
        // another way for builders is to use this DependencyUtil
        //JSONArray usages = DependenciesUtil.getDependencies(appId, version, type, keyword, request);

        List<String> appIds = new ArrayList<>();
        List<String> appNames = new ArrayList<>();

        for (Map<String, String> map : apps) {
            for (Map.Entry<String, String> entry : map.entrySet()) {
                appIds.add(entry.getKey());    
                appNames.add(entry.getValue()); 
            }
        }
        
        Map<String, Object> jsonOutput = new HashMap<>();
        jsonOutput.put("ids", appIds);
        jsonOutput.put("names", appNames);
        jsonOutput.put("jars", jarFiles);

        PrintWriter out = response.getWriter();
        out.print(new ObjectMapper().writeValueAsString(jsonOutput)); // use Jackson for proper JSON
        out.flush();
    }

    public Set<Map<String, String>> getPublishedApps(Set<String> jarFiles) {
        Connection con = null;
        PreparedStatement pstmt = null;
        ResultSet rs = null;
        Set<Map<String, String>> apps = new HashSet<>();
        
        String sql = getPublishedAppsSql();
        try {
            DataSource ds = (DataSource)AppUtil.getApplicationContext().getBean("setupDataSource");
            con = ds.getConnection();

            pstmt = con.prepareStatement(sql);
            rs = pstmt.executeQuery();

            while (rs.next()) {
                // Get columns by name
                String appId = rs.getString("appId");
                String appName = rs.getString("name");
                AppService appService = (AppService) AppUtil.getApplicationContext().getBean("appService");
                AppDefinition appDef = appService.getAppDefinition(appId, rs.getString("appVersion"));
                Collection<String> appPlugins = AppDevUtil.getPluginJarList(appDef);
                LogUtil.info(getClassName(), appId + " plugins are " + appPlugins.toString());
                // check for matches without looking at jar file versions
                Set<String> matches = appPlugins.stream()
                            .map(UninstallPluginAlert::normalizeJarName)
                            .filter(jar -> jarFiles.stream()
                            .map(UninstallPluginAlert::normalizeJarName)
                            .anyMatch(jar::equals))
                            .collect(Collectors.toSet());
                // check for matches with exact same jar file versions
                // Set<String> matches = appPlugins.stream()
                //         .filter(jarFiles::contains)
                //         .collect(Collectors.toSet());
                Map<String, String> appIdName = new HashMap<>();
                if (!matches.isEmpty()){
                    appIdName.put(appId, appName);
                    apps.add(appIdName);
                }
            }

        } catch (SQLException | BeansException e) {
            LogUtil.error(getClassName(), e, "");
        } finally {
            try {
                if (rs != null) {
                    rs.close();
                }
                if (pstmt != null) {
                    pstmt.close();
                }
                if (con != null) {
                    con.close();
                }
            } catch (SQLException e) {
                LogUtil.error(getClassName(), e, "");
            }
        }
        
        return apps;
    }

    // public Set<Map<String, String>> executeQery(String pluginClass, Set<Map<String, String>> apps) {
    //     Connection con = null;
    //     PreparedStatement pstmt = null;
    //     ResultSet rs = null;
        
    //     String sql = getSql();
    //     try {
    //         DataSource ds = (DataSource)AppUtil.getApplicationContext().getBean("setupDataSource");
    //         con = ds.getConnection();

    //         PreparedStatement ps = con.prepareStatement(sql);

    //         String searchPattern = "%" + pluginClass + "%"; // your search string
    //         int i = 1;
    //         ps.setString(i++, searchPattern); // builder.json LIKE ?
    //         ps.setString(i++, searchPattern); // form.json LIKE ?
    //         ps.setString(i++, searchPattern); // datalist.json LIKE ?
    //         ps.setString(i++, searchPattern); // userview.json LIKE ?
    //         ps.setString(i++, pluginClass);      // plugin_default.id = ?
    //         ps.setString(i++, searchPattern); // rsc.permissionProperties LIKE ?
    //         ps.setString(i++, searchPattern); // pkg_act_plugin.pluginProperties LIKE ?
    //         ps.setString(i++, searchPattern); // pkg_participant.pluginProperties LIKE ?

    //         rs = ps.executeQuery();

    //         while (rs.next()) {
    //             Map<String, String> appIdName = new HashMap<>();
    //             // Get columns by name
    //             String appId = rs.getString("appId");
    //             String appName = rs.getString("name");

    //             appIdName.put(appId, appName);
    //             apps.add(appIdName);
    //         }

    //     } catch (SQLException | BeansException e) {
    //         LogUtil.error(getClassName(), e, "");
    //     } finally {
    //         try {
    //             if (rs != null) {
    //                 rs.close();
    //             }
    //             if (pstmt != null) {
    //                 pstmt.close();
    //             }
    //             if (con != null) {
    //                 con.close();
    //             }
    //         } catch (SQLException e) {
    //             LogUtil.error(getClassName(), e, "");
    //         }
    //     }
        
    //     return apps;
    // }

    private String getPublishedAppsSql() {
        return "SELECT app.appId, app.name, app.appVersion " +
                "FROM app_app app " +
                "WHERE app.published = 1 ";
    }
    
    // private String getSql() {
    //     return "SELECT app.appId, app.name " +
    //             "FROM app_app app " +
    //             "WHERE app.published = 1 " +
    //             "AND EXISTS ( " +
    //             "    SELECT 1 FROM ( " +
    //             "        SELECT appId FROM app_builder " +
    //             "            WHERE app_builder.appId = app.appId " +
    //             "              AND app_builder.appVersion = app.appVersion " +
    //             "              AND app_builder.json LIKE ? " +
    //             "        UNION ALL " +
    //             "        SELECT appId FROM app_form " +
    //             "            WHERE app_form.appId = app.appId " +
    //             "              AND app_form.appVersion = app.appVersion " +
    //             "              AND app_form.json LIKE ? " +
    //             "        UNION ALL " +
    //             "        SELECT appId FROM app_datalist " +
    //             "            WHERE app_datalist.appId = app.appId " +
    //             "              AND app_datalist.appVersion = app.appVersion " +
    //             "              AND app_datalist.json LIKE ? " +
    //             "        UNION ALL " +
    //             "        SELECT appId FROM app_userview " +
    //             "            WHERE app_userview.appId = app.appId " +
    //             "              AND app_userview.appVersion = app.appVersion " +
    //             "              AND app_userview.json LIKE ? " +
    //             "        UNION ALL " +
    //             "        SELECT appId FROM app_plugin_default " +
    //             "            WHERE app_plugin_default.appId = app.appId " +
    //             "              AND app_plugin_default.appVersion = app.appVersion " +
    //             "              AND app_plugin_default.id = ? " +
    //             "        UNION ALL " +
    //             "        SELECT appId FROM app_resource " +
    //             "            WHERE app_resource.appId = app.appId " +
    //             "              AND app_resource.appVersion = app.appVersion " +
    //             "              AND app_resource.permissionProperties LIKE ? " +
    //             "        UNION ALL " +
    //             "        SELECT p.appId FROM app_package_activity_plugin " +
    //             "            INNER JOIN app_package p ON p.packageId = app_package_activity_plugin.packageId " +
    //             "              AND p.packageVersion = app_package_activity_plugin.packageVersion " +
    //             "            WHERE p.appId = app.appId " +
    //             "              AND p.appVersion = app.appVersion " +
    //             "              AND app_package_activity_plugin.pluginProperties LIKE ? " +
    //             "        UNION ALL " +
    //             "        SELECT appId FROM app_package_participant " +
    //             "            INNER JOIN app_package p ON p.packageId = app_package_participant.packageId " +
    //             "              AND p.packageVersion = app_package_participant.packageVersion " +
    //             "            WHERE p.appId = app.appId " +
    //             "              AND p.appVersion = app.appVersion " +
    //             "              AND app_package_participant.pluginProperties LIKE ? " +
    //             "    ) AS tmp " +  
    //             ")";
    // }

    private static String normalizeJarName(String filename) {
        if (filename == null) return "";
        // Trim spaces
        filename = filename.trim();
        // Remove .jar extension
        filename = filename.replaceAll("\\.jar$", "");
        // Remove Windows copy suffix like " (1)", " (2)"
        filename = filename.replaceAll("\\s*\\(\\d+\\)$", "");
        // Remove version suffixes like -1.2.3, -v2.0.1, -SNAPSHOT, -beta
        filename = filename.replaceAll("(-v?\\d+(\\.\\d+)*(-SNAPSHOT)?(-[a-zA-Z]+)?)$", "");
        return filename;
    }

}
