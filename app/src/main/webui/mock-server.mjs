import { createServer } from 'http';
import { readFileSync, existsSync, statSync } from 'fs';
import { join, extname } from 'path';
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

  if (path.startsWith('/api/devtown/reviews/') && !path.endsWith('/reviewers') && !path.endsWith('/contributors') && !path.includes('/reviewers/') && !path.includes('/contributors/')) {
    const id = path.split('/').pop();
    const detail = reviewDetails[id] || template;
    res.writeHead(200, { 'Content-Type': 'application/json', 'Access-Control-Allow-Origin': '*' });
    res.end(JSON.stringify(detail));
    return;
  }

  const filePath = join(staticDir, path === '/' ? 'index.html' : path);
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
