package com.vn.jet.mosco.spinserver.controller;

import com.vn.jet.mosco.spinserver.service.AssetManagementService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Trang quản trị tài nguyên Objet — Galactic Dark Mode Dashboard.
 * Truy cập: http://localhost:8080/admin/assets?key=ADMIN_SECRET
 */
@RestController
@RequestMapping("/admin")
public class AdminController {

    @Autowired
    private AssetManagementService assetService;

    @Value("${ADMIN_SECRET:mosco_admin_2026}")
    private String adminSecret;

    @GetMapping(value = "/assets", produces = MediaType.TEXT_HTML_VALUE)
    public ResponseEntity<String> dashboard(@RequestParam(value = "key", defaultValue = "") String key) {
        // Bảo mật: Kiểm tra Secret Key
        if (!adminSecret.equals(key)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body("<html><body style='background:#0a0a0f;color:#ff4444;font-family:monospace;display:flex;justify-content:center;align-items:center;height:100vh;margin:0'>"
                            + "<h1>🔒 ACCESS DENIED — Invalid Key</h1></body></html>");
        }

        String html = """
                <!DOCTYPE html>
                <html lang="en">
                <head>
                    <meta charset="UTF-8">
                    <meta name="viewport" content="width=device-width, initial-scale=1.0">
                    <title>Mosco — Asset Control Center</title>
                    <style>
                        * { margin: 0; padding: 0; box-sizing: border-box; }
                        body {
                            background: #0a0a0f;
                            color: #e0e0e0;
                            font-family: 'Segoe UI', 'Inter', sans-serif;
                            min-height: 100vh;
                            padding: 24px;
                        }
                        .header {
                            text-align: center;
                            margin-bottom: 32px;
                            padding: 24px;
                            background: linear-gradient(135deg, rgba(88,28,135,0.3), rgba(15,23,42,0.8));
                            border: 1px solid rgba(139,92,246,0.3);
                            border-radius: 16px;
                        }
                        .header h1 {
                            font-size: 28px;
                            background: linear-gradient(135deg, #a78bfa, #60a5fa);
                            -webkit-background-clip: text;
                            -webkit-text-fill-color: transparent;
                            margin-bottom: 8px;
                        }
                        .header p { color: #94a3b8; font-size: 14px; }
                        .grid {
                            display: grid;
                            grid-template-columns: repeat(auto-fit, minmax(280px, 1fr));
                            gap: 16px;
                            margin-bottom: 24px;
                        }
                        .card {
                            background: rgba(15, 23, 42, 0.6);
                            border: 1px solid rgba(139,92,246,0.2);
                            border-radius: 12px;
                            padding: 20px;
                            backdrop-filter: blur(10px);
                        }
                        .card h3 {
                            color: #a78bfa;
                            font-size: 13px;
                            text-transform: uppercase;
                            letter-spacing: 1px;
                            margin-bottom: 8px;
                        }
                        .card .value {
                            font-size: 32px;
                            font-weight: 700;
                            color: #f1f5f9;
                        }
                        .card .sub { color: #64748b; font-size: 12px; margin-top: 4px; }
                        .status-badge {
                            display: inline-block;
                            padding: 4px 12px;
                            border-radius: 20px;
                            font-size: 12px;
                            font-weight: 600;
                        }
                        .status-idle { background: rgba(34,197,94,0.15); color: #22c55e; }
                        .status-busy { background: rgba(234,179,8,0.15); color: #eab308; }
                        .actions {
                            display: flex;
                            gap: 12px;
                            flex-wrap: wrap;
                            margin-bottom: 24px;
                        }
                        .btn {
                            padding: 12px 24px;
                            border: none;
                            border-radius: 10px;
                            font-size: 14px;
                            font-weight: 600;
                            cursor: pointer;
                            transition: all 0.2s;
                        }
                        .btn:hover { transform: translateY(-2px); }
                        .btn:active { transform: translateY(0); }
                        .btn-primary {
                            background: linear-gradient(135deg, #7c3aed, #3b82f6);
                            color: white;
                        }
                        .btn-secondary {
                            background: rgba(51, 65, 85, 0.6);
                            color: #e2e8f0;
                            border: 1px solid rgba(139,92,246,0.3);
                        }
                        .btn:disabled {
                            opacity: 0.5;
                            cursor: not-allowed;
                            transform: none;
                        }
                        .log-area {
                            background: rgba(2, 6, 23, 0.8);
                            border: 1px solid rgba(51,65,85,0.5);
                            border-radius: 12px;
                            padding: 16px;
                            font-family: 'Cascadia Code', 'Fira Code', monospace;
                            font-size: 13px;
                            color: #94a3b8;
                            min-height: 200px;
                            max-height: 400px;
                            overflow-y: auto;
                            white-space: pre-wrap;
                        }
                        .log-area .log-entry { margin-bottom: 4px; }
                        .log-area .log-time { color: #475569; }
                        .log-area .log-ok { color: #22c55e; }
                        .log-area .log-warn { color: #eab308; }
                        @keyframes pulse { 0%,100% { opacity:1; } 50% { opacity:0.5; } }
                        .pulsing { animation: pulse 1.5s infinite; }
                    </style>
                </head>
                <body>
                    <div class="header">
                        <h1>🌌 Mosco — Asset Control Center</h1>
                        <p>Galactic Resource Management Dashboard</p>
                    </div>

                    <div class="grid">
                        <div class="card">
                            <h3>Sync Status</h3>
                            <div class="value" id="statusBadge">
                                <span class="status-badge status-idle">IDLE</span>
                            </div>
                            <div class="sub" id="statusDetail">Ready for commands</div>
                        </div>
                        <div class="card">
                            <h3>Total Images</h3>
                            <div class="value" id="totalImages">—</div>
                            <div class="sub">Front + Back assets on disk</div>
                        </div>
                        <div class="card">
                            <h3>Bundles</h3>
                            <div class="value" id="totalBundles">—</div>
                            <div class="sub">Sealed + Patches</div>
                        </div>
                        <div class="card">
                            <h3>Last Sync</h3>
                            <div class="value" id="lastSync" style="font-size:18px">—</div>
                            <div class="sub">Last successful synchronization</div>
                        </div>
                    </div>

                    <div class="actions">
                        <button class="btn btn-primary" id="btnSync" onclick="triggerSync()">
                            🚀 SYNC NOW
                        </button>
                        <button class="btn btn-secondary" id="btnRebuild" onclick="triggerRebuild()">
                            📦 REBUILD BUNDLES
                        </button>
                        <button class="btn btn-secondary" onclick="refreshDashboard()">
                            🔄 REFRESH
                        </button>
                    </div>

                    <div class="log-area" id="logArea">
                        <div class="log-entry"><span class="log-time">[SYSTEM]</span> Dashboard initialized. Ready.</div>
                    </div>

                    <script>
                        const LOG = document.getElementById('logArea');
                        let pollInterval = null;

                        function addLog(msg, type = '') {
                            const time = new Date().toLocaleTimeString();
                            const cls = type === 'ok' ? 'log-ok' : type === 'warn' ? 'log-warn' : '';
                            LOG.innerHTML += `<div class="log-entry"><span class="log-time">[${time}]</span> <span class="${cls}">${msg}</span></div>`;
                            LOG.scrollTop = LOG.scrollHeight;
                        }

                        async function triggerSync() {
                            document.getElementById('btnSync').disabled = true;
                            addLog('🚀 Triggering asset synchronization...');
                            try {
                                const res = await fetch('/api/assets/sync', { method: 'POST' });
                                const text = await res.text();
                                addLog(text, 'ok');
                                startPolling();
                            } catch (e) {
                                addLog('❌ Error: ' + e.message, 'warn');
                                document.getElementById('btnSync').disabled = false;
                            }
                        }

                        async function triggerRebuild() {
                            if (!confirm('Rebuild sẽ nén lại TOÀN BỘ Sealed Bundles. Tiếp tục?')) return;
                            document.getElementById('btnRebuild').disabled = true;
                            addLog('📦 Triggering full bundle rebuild...');
                            try {
                                const res = await fetch('/api/assets/rebuild', { method: 'POST' });
                                const text = await res.text();
                                addLog(text, 'ok');
                                startPolling();
                            } catch (e) {
                                addLog('❌ Error: ' + e.message, 'warn');
                                document.getElementById('btnRebuild').disabled = false;
                            }
                        }

                        function startPolling() {
                            if (pollInterval) clearInterval(pollInterval);
                            pollInterval = setInterval(pollStatus, 2000);
                        }

                        async function pollStatus() {
                            try {
                                const res = await fetch('/api/assets/status');
                                const data = await res.json();
                                const badge = document.getElementById('statusBadge');
                                const detail = document.getElementById('statusDetail');

                                if (data.status === 'IDLE') {
                                    badge.innerHTML = '<span class="status-badge status-idle">IDLE</span>';
                                    detail.textContent = data.detail || 'Ready for commands';
                                    clearInterval(pollInterval);
                                    pollInterval = null;
                                    document.getElementById('btnSync').disabled = false;
                                    document.getElementById('btnRebuild').disabled = false;
                                    addLog('✅ Process completed.', 'ok');
                                    refreshDashboard();
                                } else {
                                    badge.innerHTML = '<span class="status-badge status-busy pulsing">' + data.status + '</span>';
                                    detail.textContent = data.detail;
                                }
                            } catch (e) { /* ignore polling errors */ }
                        }

                        async function refreshDashboard() {
                            try {
                                const res = await fetch('/api/assets/manifest');
                                if (res.ok) {
                                    const manifest = await res.json();
                                    document.getElementById('totalImages').textContent =
                                        (manifest.totalImages || 0).toLocaleString();
                                    const bundles = (manifest.sealedBundles || []).length;
                                    const patches = (manifest.patches || []).length;
                                    document.getElementById('totalBundles').textContent =
                                        bundles + ' + ' + patches;
                                    if (manifest.lastSync) {
                                        document.getElementById('lastSync').textContent =
                                            new Date(manifest.lastSync).toLocaleString();
                                    }
                                }
                            } catch (e) {
                                addLog('⚠️ Cannot load manifest: ' + e.message, 'warn');
                            }
                        }

                        // Khởi tạo
                        refreshDashboard();
                        pollStatus();
                    </script>
                </body>
                </html>
                """;

        return ResponseEntity.ok(html);
    }
}
