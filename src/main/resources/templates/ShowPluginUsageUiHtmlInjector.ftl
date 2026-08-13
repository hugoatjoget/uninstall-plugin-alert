<style>
    /* Hint that rows in the bundle-contents popup are clickable */
    #pluginList1 table.jsontable tbody tr:hover {
        cursor: pointer;
        background-color: #f5f5f5;
    }
</style>
<script>
    // Bound with plain addEventListener (not $(document).ready + jQuery .on())
    // deliberately: this popup uses a lighter popupHeader/popupFooter layout
    // than the main console shell, and jQuery is not guaranteed to have
    // finished loading by the time this injected script tag runs - binding to
    // "document" itself needs no library and can never race a script-load
    // order issue, since document always exists. jQuery/UI/Swal are only
    // touched inside the handler below, which only runs on an actual click -
    // by then the whole page (jQuery included) will have long finished
    // loading, so it's safe to rely on them there.
    //
    // Bound on the CAPTURE phase (the trailing "true"), not bubble phase: this
    // table's own native click handler (registered by Joget's ui.js for every
    // <ui:jsontable>, bound bubble-phase on #pluginList1) returns false for
    // every click inside a row of this particular table - it has no href/link
    // and no checkbox column, so it always falls into the branch that ends in
    // "return false", which stops the click from bubbling any further. A
    // bubble-phase listener on document would never see those clicks. Capture
    // fires top-down *before* the event reaches the row and before that
    // bubble-phase handler runs, so it's unaffected by anything it does.
    document.addEventListener('click', function(event) {
        var row = event.target.closest && event.target.closest('#pluginList1 tbody tr');
        if (!row || !row.id) {
            return;
        }
        // Row id is built by ui.js as "row" + escaped fully-qualified class
        // name (dots replaced with "__dot__") - same convention the console
        // itself uses for row identity, e.g. row"org__dot__joget__dot__...".
        var pluginClass = row.id.substring(3).replace(/__dot__/g, '.');

        // Rows in this popup currently have no click behavior of their own
        // (see PluginJsonController#pluginListBundlePlugins), so this doesn't
        // conflict with anything else on the page.
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
    }, true);
</script>
