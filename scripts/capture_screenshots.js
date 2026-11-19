const { spawn } = require('child_process');
const fs = require('fs');
const path = require('path');
const WebSocket = require(path.join(__dirname, '../frontend/node_modules/ws'));

const CHROME_PATH = '/Applications/Google Chrome.app/Contents/MacOS/Google Chrome';
const OUT_DIR = path.join(__dirname, '../docs/screenshots');

if (!fs.existsSync(OUT_DIR)) {
  fs.mkdirSync(OUT_DIR, { recursive: true });
}

async function sleep(ms) {
  return new Promise(resolve => setTimeout(resolve, ms));
}

async function fetchJson(url) {
  const res = await fetch(url);
  return await res.json();
}

class ChromeClient {
  constructor(ws) {
    this.ws = ws;
    this.id = 1;
    this.callbacks = new Map();
    ws.on('message', data => {
      const msg = JSON.parse(data.toString());
      if (msg.id && this.callbacks.has(msg.id)) {
        const { resolve, reject } = this.callbacks.get(msg.id);
        this.callbacks.delete(msg.id);
        if (msg.error) reject(new Error(msg.error.message));
        else resolve(msg.result);
      }
    });
  }

  send(method, params = {}) {
    const id = this.id++;
    return new Promise((resolve, reject) => {
      this.callbacks.set(id, { resolve, reject });
      this.ws.send(JSON.stringify({ id, method, params }));
    });
  }

  async eval(expr) {
    const res = await this.send('Runtime.evaluate', { expression: expr, awaitPromise: true });
    return res.result ? res.result.value : null;
  }

  async click(selector) {
    await this.eval(`(() => {
      const el = document.querySelector(${JSON.stringify(selector)});
      if (el) { el.click(); return true; }
      return false;
    })()`);
  }

  async clickText(text) {
    await this.eval(`(() => {
      const els = Array.from(document.querySelectorAll('button, a, span, div'));
      const target = els.find(e => e.textContent && e.textContent.includes(${JSON.stringify(text)}));
      if (target) { target.click(); return true; }
      return false;
    })()`);
  }

  async screenshot(filename) {
    const res = await this.send('Page.captureScreenshot', { format: 'png' });
    const buf = Buffer.from(res.data, 'base64');
    fs.writeFileSync(path.join(OUT_DIR, filename), buf);
    console.log(`Saved screenshot: ${filename} (${buf.length} bytes)`);
  }
}

async function run() {
  console.log('Launching headless Chrome...');
  const chrome = spawn(CHROME_PATH, [
    '--headless',
    '--disable-gpu',
    '--remote-debugging-port=9222',
    '--window-size=1440,900',
    'about:blank'
  ]);

  let client = null;

  try {
    // Wait for debugging port
    let wsUrl = null;
    for (let i = 0; i < 20; i++) {
      await sleep(300);
      try {
        const targets = await fetchJson('http://127.0.0.1:9222/json/list');
        if (targets && targets.length > 0 && targets[0].webSocketDebuggerUrl) {
          wsUrl = targets[0].webSocketDebuggerUrl;
          break;
        }
      } catch (e) {
        // retry
      }
    }

    if (!wsUrl) throw new Error('Could not connect to Chrome debugging port');

    console.log('Connecting WebSocket:', wsUrl);
    const ws = new WebSocket(wsUrl);
    await new Promise(r => ws.on('open', r));
    client = new ChromeClient(ws);

    await client.send('Page.enable');
    await client.send('Runtime.enable');
    await client.send('Emulation.setDeviceMetricsOverride', {
      width: 1440,
      height: 900,
      deviceScaleFactor: 2,
      mobile: false
    });

    console.log('Navigating to http://localhost:5173...');
    await client.send('Page.navigate', { url: 'http://localhost:5173' });
    await sleep(2000);

    // 1. Procurement Tab: Run AI Extraction & Landed Cost
    console.log('1. Capturing Procurement AI flow...');
    await client.eval(`(() => {
      const btn = Array.from(document.querySelectorAll('button')).find(b => b.textContent && b.textContent.includes('Extract Quote with AI'));
      if (btn) btn.click();
    })()`);
    await sleep(1500);
    await client.eval(`(() => {
      const btn = Array.from(document.querySelectorAll('button')).find(b => b.textContent && b.textContent.includes('Run Deterministic Landed Cost Evaluation'));
      if (btn) btn.click();
    })()`);
    await sleep(1500);
    await client.screenshot('01_procurement_ai.png');

    // 2. Commercial Tab: BOM & Waterfall (index 1)
    console.log('2. Capturing Commercial & Quotations tab...');
    await client.eval(`document.querySelectorAll('.nav-tab')[1].click()`);
    await sleep(1500);
    await client.screenshot('02_commercial_bom.png');

    // 3. Approvals Gateway (index 2)
    console.log('3. Capturing Approvals Gateway tab...');
    await client.eval(`document.querySelectorAll('.nav-tab')[2].click()`);
    await sleep(1500);
    await client.screenshot('03_approvals_gateway.png');

    // 4. Operations Dashboard (index 3)
    console.log('4. Capturing Operations Dashboard tab...');
    await client.eval(`document.querySelectorAll('.nav-tab')[3].click()`);
    await sleep(1500);
    await client.screenshot('04_dashboard_financial.png');

    // 5. Guided E2E Demo (index 4)
    console.log('5. Running and capturing Guided E2E Demo...');
    await client.eval(`document.querySelectorAll('.nav-tab')[4].click()`);
    await sleep(1500);
    await client.eval(`(() => {
      const btn = Array.from(document.querySelectorAll('button')).find(b => b.textContent && (b.textContent.includes('Run Full Guided Demonstration') || b.textContent.includes('Run Full End-to-End Journey')));
      if (btn) btn.click();
    })()`);
    console.log('Waiting for autonomous journey to complete (14s)...');
    await sleep(14000);
    await client.screenshot('05_guided_demo_celebration.png');

    // 6. Mobile Viewport (390x844)
    console.log('6. Capturing Mobile 390px responsive layouts...');
    await client.send('Emulation.setDeviceMetricsOverride', {
      width: 390,
      height: 844,
      deviceScaleFactor: 2,
      mobile: true
    });
    await client.eval(`document.querySelectorAll('.nav-tab')[3].click()`);
    await sleep(1500);
    await client.screenshot('06_mobile_390px_dashboard.png');

    await client.eval(`document.querySelectorAll('.nav-tab')[2].click()`);
    await sleep(1500);
    await client.screenshot('07_mobile_390px_approvals.png');

    console.log('All screenshots captured successfully!');
  } finally {
    if (client) {
      try { client.ws.close(); } catch (_) {}
    }
    chrome.kill('SIGKILL');
  }
}

run().catch(err => {
  console.error('Screenshot run failed:', err);
  process.exit(1);
});
