<style>
    /* Hint that rows in the bundle-contents popup are clickable */
    #pluginList1 table.jsontable tbody tr:hover {
        cursor: pointer;
        background-color: #f5f5f5;
    }
</style>
<script>
    $(document).ready(function() {
        // Rows in this popup currently have no click behavior of their own
        // (see PluginJsonController#pluginListBundlePlugins), so this is safe
        // to bind without conflicting with anything else on the page.
        $('#pluginList1').on('click', 'tbody tr', function() {
            var rowId = $(this).attr('id');
            if (!rowId) {
                return;
            }
            // Row id is built by ui.js as "row" + escaped fully-qualified class
            // name (dots replaced with "__dot__") - same convention the console
            // itself uses for row identity, e.g. row"org__dot__joget__dot__...".
            var pluginClass = rowId.substring(3).replace(/__dot__/g, '.');

            // This popup uses a lighter layout than the main console shell, so
            // don't assume UI.js/SweetAlert2 are loaded here - degrade to plain
            // alert() if they aren't.
            var hasToast = (typeof UI !== 'undefined' && typeof UI.showConsoleToast === 'function');
            var hasSwal = (typeof Swal !== 'undefined');

            if (hasToast) {
                UI.showConsoleToast(0, 'Checking for plugin usage, please wait...', 'fas fa-spinner fa-spin', 10000, $('body'));
            }

            // Reuse the usage-detection web service already exposed by
            // UninstallPluginAlert instead of duplicating that logic here.
            $.ajax({
                url: "/jw/web/json/plugin/org.joget.marketplace.UninstallPluginAlert/service",
                type: "POST",
                contentType: "application/json",
                dataType: "json",
                data: JSON.stringify({ selectedList: [pluginClass] }),
                success: function (response) {
                    if (hasToast) {
                        $('.toast#toast-0').remove();
                    }
                    var plugins = response.names || [];
                    var message;
                    if (plugins.length > 0) {
                        message = 'Apps using this plugin:<ul style="text-align:left;">';
                        for (var i = 0; i < plugins.length; i++) {
                            message += '<li>' + plugins[i] + '</li>';
                        }
                        message += '</ul>';
                    } else {
                        message = 'No published apps are currently using this plugin.';
                    }
                    if (hasSwal) {
                        Swal.fire({
                            icon: 'info',
                            title: 'Show Usages',
                            html: message,
                            confirmButtonText: 'OK'
                        });
                    } else {
                        alert('Show Usages: ' + pluginClass + '\n\n' + $('<div>').html(message).text());
                    }
                },
                error: function () {
                    if (hasToast) {
                        $('.toast#toast-0').remove();
                    }
                    alert('Failed to check plugin usage. Please try again.');
                }
            });
        });
    });
</script>
