<style>
    /* Hint that rows in the bundle-contents popup are clickable */
    #pluginList1 table.jsontable tbody tr:hover {
        cursor: pointer;
        background-color: #f5f5f5;
    }

    /* A site-wide stylesheet forces "list-style: disc !important" (or, for
       plain Swal.fire() popups with no customClass like this one, some other
       equally-!important reset) on every <li> inside a SweetAlert2 popup's
       .swal2-html-container, which stomps our <ol>'s numbering. List several
       selectors of increasing specificity for the same declaration - each is
       evaluated independently per matching element, so this only adds
       coverage against whichever exact ancestor structure this particular
       popup turns out to have, without risking anything already working. */
    .show-usages-list li,
    .swal2-html-container .show-usages-list li,
    .dialog-swal-popup .swal2-html-container .show-usages-list li {
        list-style: decimal !important;
    }
    .show-usages-list a {
        color: #1677ff !important;
        text-decoration: underline;
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
        //
        // This popup's lighter layout turned out to not load Joget's UI.js
        // helpers at all (UI.showConsoleToast never appeared, confirmed by
        // live testing), even though SweetAlert2 itself is available here
        // (the result dialog renders fine) - so the loading indicator is
        // built entirely on Swal's own built-in loading state instead of
        // UI.showConsoleToast, matching what this page actually has rather
        // than what we assumed it might have.
        var hasSwal = (typeof Swal !== 'undefined');

        // Checking a single class resolves far faster than
        // UninstallPluginAlert's "scan every selected jar against every
        // published app" case - enforce a minimum display time so the
        // loading state is still perceptible even on a near-instant response,
        // instead of Swal replacing it before the transition is even visible.
        var LOADING_MIN_VISIBLE_MS = 600;
        var loadingShownAt = null;
        if (hasSwal) {
            Swal.fire({
                title: 'Checking for plugin usage, please wait...',
                allowOutsideClick: false,
                allowEscapeKey: false,
                showConfirmButton: false,
                didOpen: function () {
                    Swal.showLoading();
                }
            });
            loadingShownAt = Date.now();
        }

        function afterMinimumLoadingTime(callback) {
            if (!hasSwal) {
                callback();
                return;
            }
            var remaining = LOADING_MIN_VISIBLE_MS - (Date.now() - loadingShownAt);
            setTimeout(callback, Math.max(remaining, 0));
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
                var plugins = response.names || [];
                var ids = response.ids || [];
                var versions = response.versions || [];
                var message;
                if (plugins.length > 0) {
                    // Each app name links straight to its App Composer
                    // builders page (needs both id and version to build that
                    // URL) so an admin can jump right into the app.
                    message = 'Published apps using this plugin:<ol class="show-usages-list" style="text-align:left;">';
                    for (var i = 0; i < plugins.length; i++) {
                        var builderUrl = '/jw/web/console/app/' + encodeURIComponent(ids[i]) + '/' + encodeURIComponent(versions[i]) + '/builders';
                        message += '<li><a href="' + builderUrl + '" target="_blank" rel="noopener">' + plugins[i] + '</a></li>';
                    }
                    message += '</ol>';
                } else {
                    message = 'No published apps are currently using this plugin.';
                }
                afterMinimumLoadingTime(function () {
                    if (hasSwal) {
                        // Calling Swal.fire() again while the loading popup is
                        // still open replaces it with this new content - no
                        // manual teardown of the loading state needed.
                        Swal.fire({
                            icon: 'info',
                            title: 'Show Usages',
                            html: message,
                            confirmButtonText: 'OK'
                        });
                    } else {
                        alert('Show Usages: ' + pluginClass + '\n\n' + $('<div>').html(message).text());
                    }
                });
            },
            error: function () {
                afterMinimumLoadingTime(function () {
                    if (hasSwal) {
                        Swal.fire({
                            icon: 'error',
                            title: 'Failed to check plugin usage',
                            text: 'Please try again.',
                            confirmButtonText: 'OK'
                        });
                    } else {
                        alert('Failed to check plugin usage. Please try again.');
                    }
                });
            }
        });
    }, true);
</script>
