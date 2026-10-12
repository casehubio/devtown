import { createServer } from 'http';
import { readFileSync, existsSync, statSync } from 'fs';
import { join, extname, resolve } from 'path';
import { fileURLToPath } from 'url';

const __dir = fileURLToPath(new URL('.', import.meta.url));
const fixtures = JSON.parse(readFileSync(join(__dir, 'mock-fixtures.json'), 'utf-8'));
const staticDir = process.argv[2] || join(__dir, '..', '..', '..', '..', 'target', 'quinoa', 'build');
const port = parseInt(process.argv[3] || '8280', 10);

const template = fixtures['_review_detail_template'];
const reviewDetails = {};
for (const r of (fixtures['/api/devtown/governance/queue-status']?.reviews ?? [])) {
  reviewDetails[r.caseId] = {
    ...template,
    caseId: r.caseId,
    pr: { repo: r.repo, prNumber: r.prNumber, contributor: r.contributor, linesChanged: r.linesChanged, headSha: 'abc1234' },
  };
}

const MIME = { '.html': 'text/html', '.js': 'application/javascript', '.css': 'text/css', '.json': 'application/json', '.png': 'image/png', '.svg': 'image/svg+xml' };

createServer((req, res) => {
  const path = req.url.split('?')[0];

  if (req.method === 'POST') {
    res.writeHead(200, { 'Content-Type': 'application/json' });
    res.end(JSON.stringify({ action: 'mock', result: 'OK' }));
    return;
  }

  if (fixtures[path]) {
    res.writeHead(200, { 'Content-Type': 'application/json', 'Access-Control-Allow-Origin': '*' });
    res.end(JSON.stringify(fixtures[path]));
    return;
  }

  if (path.startsWith('/api/sessions/') && path !== '/api/sessions') {
    const parts = path.split('/');
    const sessionId = parts[3];
    const sub = parts[4];
    const session = (fixtures['/api/sessions'] ?? []).find(s => s.id === sessionId);
    if (sub === 'output') {
      res.writeHead(200, { 'Content-Type': 'text/plain', 'Access-Control-Allow-Origin': '*' });
      res.end(session
        ? `[${session.createdAt}] Session ${session.name} started\n[${session.createdAt}] Working directory: ${session.workingDir}\n[${session.lastActive}] Status: ${session.status}\n[${session.lastActive}] Role: ${session.roleName ?? 'none'}\n${session.caseId ? `[${session.lastActive}] Active case: ${session.caseId}\n` : ''}[${session.lastActive}] Ready for commands.\n`
        : 'Session not found\n');
      return;
    }
    if (sub === 'git-status') {
      res.writeHead(200, { 'Content-Type': 'application/json', 'Access-Control-Allow-Origin': '*' });
      res.end(JSON.stringify({ gitRepo: true, githubRepo: session?.workingDir?.split('/').slice(-2).join('/') ?? 'unknown/repo', branch: 'main', pr: null }));
      return;
    }
    if (sub === 'service-health') {
      res.writeHead(200, { 'Content-Type': 'application/json', 'Access-Control-Allow-Origin': '*' });
      res.end(JSON.stringify([{ port: 8080, up: session?.status === 'ACTIVE', responseMs: session?.status === 'ACTIVE' ? 12 : 0 }]));
      return;
    }
    res.writeHead(200, { 'Content-Type': 'application/json', 'Access-Control-Allow-Origin': '*' });
    res.end(JSON.stringify(session ?? {}));
    return;
  }

  if (path.startsWith('/api/devtown/reviews/') && !path.endsWith('/reviewers') && !path.endsWith('/contributors') && !path.includes('/reviewers/') && !path.includes('/contributors/')) {
    const id = path.split('/').pop();
    const detail = reviewDetails[id] || template;
    res.writeHead(200, { 'Content-Type': 'application/json', 'Access-Control-Allow-Origin': '*' });
    res.end(JSON.stringify(detail));
    return;
  }

  const filePath = resolve(staticDir, (path === '/' ? 'index.html' : path).replace(/^\//, ''));
  if (!filePath.startsWith(resolve(staticDir))) { res.writeHead(403); res.end('Forbidden'); return; }
  if (existsSync(filePath) && statSync(filePath).isFile()) {
    const mime = MIME[extname(filePath)] || 'application/octet-stream';
    res.writeHead(200, { 'Content-Type': mime });
    res.end(readFileSync(filePath));
    return;
  }

  res.writeHead(404);
  res.end('Not found');
}).listen(port, () => {
  console.log(`Mock devtown server: http://localhost:${port}`);
  console.log(`Static files: ${staticDir}`);
  console.log(`Fixtures: ${Object.keys(fixtures).filter(k => !k.startsWith('_')).length} endpoints`);
});
