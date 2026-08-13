package org.joget.marketplace;

import java.util.HashMap;
import java.util.Map;

import jakarta.servlet.http.HttpServletRequest;

import org.joget.apps.app.model.UiHtmlInjectorPluginAbstract;
import org.joget.apps.app.service.AppUtil;
import org.joget.plugin.base.PluginManager;

/**
 * UI Html Injector plugin that lets admins click a plugin class row inside the
 * "Manage Plugins" bundle-contents popup (opened by clicking a bundle row on
 * /web/console/setting/plugin) to see which published apps use that plugin.
 *
 * Reuses the usage-detection web service already exposed by
 * {@link UninstallPluginAlert}, so this class only needs to inject the click
 * handler and render the result - it doesn't duplicate the app-scanning logic.
 */
public class ShowPluginUsage extends UiHtmlInjectorPluginAbstract {

    @Override
    public String getName() {
        return "Show Plugin Usage";
    }

    @Override
    public String getVersion() {
        return "9.1.1";
    }

    @Override
    public String getDescription() {
        return "UI Html Injector plugin that lets admins click a plugin class row in the Manage Plugins bundle-contents popup to see which published apps use it.";
    }

    @Override
    public String[] getInjectUrlPatterns() {
        // The bundle-contents popup opened by clicking a plugin row on the main
        // plugin list is served at this URL (loaded inside an iframe), not the
        // main list page - so it needs its own injection pattern.
        return new String[] {"/web/console/setting/plugin/details"};
    }

    @Override
    public String getHtml(HttpServletRequest request) {
        PluginManager pluginManager = (PluginManager) AppUtil.getApplicationContext().getBean("pluginManager");
        Map data = new HashMap();
        data.put("plugin", this);
        data.put("request", request);

        return pluginManager.getPluginFreeMarkerTemplate(data, getClassName(), "/templates/ShowPluginUsageUiHtmlInjector.ftl", null);
    }

    @Override
    public boolean isIncludeForAjaxThemePageSwitching() {
        return false;
    }
}
