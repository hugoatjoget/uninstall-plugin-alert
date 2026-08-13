<script>
    window.uninstall = function(selectedList) {
        console.log(selectedList);
        // Show a toast immediately - checking plugin usage across all published
        // apps can take a few seconds, so give the user feedback instead of
        // leaving the page looking unresponsive. Auto-dismisses after 10s as a
        // safety net, but we remove it as soon as the response comes back.
        UI.showConsoleToast(0, 'Checking for plugin usage, please wait...', 'fas fa-spinner fa-spin', 10000, $('#main'));
        $.ajax({
            url: "/jw/web/json/plugin/org.joget.marketplace.UninstallPluginAlert/service",
            type: "POST",
            contentType: "application/json",
            dataType: "json",
            data: JSON.stringify({ selectedList: selectedList}),
            success: function (response) {
              $('.toast#toast-0').remove();
              var plugins = response.names || []; // already an array
              var ids = response.ids || [];
              var versions = response.versions || [];
              var message = 'Are you sure you want to uninstall the selected Plugin(s)?';
              if (plugins.length > 0) {
                // Each app name links straight to its App Composer builders
                // page (needs both id and version to build that URL), so an
                // admin can go check the app before deciding to proceed.
                message += '<br><br>There are apps using this plugin(s):<ol style="text-align:left;">';
                for (var i = 0; i < plugins.length; i++) {
                    var builderUrl = '/jw/web/console/app/' + encodeURIComponent(ids[i]) + '/' + encodeURIComponent(versions[i]) + '/builders';
                    message += '<li><a href="' + builderUrl + '" target="_blank" rel="noopener">' + plugins[i] + '</a></li>';
                }
                message += '</ol>';
              }
              // console.log(decodeURIComponent(response.jars));
              UI.confirm(message, function() {
                  UI.blockUI();
                  var callback = {
                      success: function() {
                          document.location = '/jw/web/console/setting/plugin';
                      }
                  };
                  var request = ConnectionManager.post(
                      '/jw/web/console/setting/plugin/uninstall',
                      callback,
                      'selectedPlugins=' + selectedList
                  );
              }, { isHtml: true });
            },
            error: function (xhr, status, error) {
              $('.toast#toast-0').remove();
              alert('Failed to check plugin usage before uninstalling. Please try again.');
            }
        });
    };
</script>
