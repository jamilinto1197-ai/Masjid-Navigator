const fs = require('fs');
const html = fs.readFileSync('www/index.html', 'utf8');
if (!html.includes('<html') || !html.includes('</html>')) throw new Error('www/index.html is not a complete HTML document');
console.log('HTML document check passed.');
