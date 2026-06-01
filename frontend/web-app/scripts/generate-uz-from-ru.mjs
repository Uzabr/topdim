import { readFileSync, writeFileSync } from 'fs';
import { fileURLToPath } from 'url';
import { dirname, join } from 'path';

const dir = join(dirname(fileURLToPath(import.meta.url)), '../src/locales');
const ru = readFileSync(join(dir, 'ru.json'), 'utf8');
writeFileSync(join(dir, 'uz.json'), ru);
console.log('uz.json synced from ru.json');
