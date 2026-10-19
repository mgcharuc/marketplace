// Ningún archivo recorta textos ni muestra imágenes sueltas. Para eso están ProductName y
// ProductImage, y este chequeo falla si alguien los esquiva.
import { readdirSync, readFileSync } from 'node:fs';
import { join, relative, sep } from 'node:path';

const ROOT = 'src';
const IMAGE_COMPONENT = 'src/app/ui/product-image.ts';
const RULES = [
  {
    pattern: /<img\b/,
    message: 'usar <app-product-image> en lugar de <img>',
    skip: IMAGE_COMPONENT,
  },
  {
    pattern: /text-overflow|textOverflow|text-ellipsis|text-clip|\btruncate\b/,
    message: 'no recortar textos, usar <app-product-name>',
  },
  { pattern: /line-clamp|lineClamp/, message: 'no recortar textos, usar <app-product-name>' },
];

function files(dir) {
  return readdirSync(dir, { withFileTypes: true }).flatMap((entry) => {
    const path = join(dir, entry.name);
    if (entry.isDirectory()) return files(path);
    return /\.(ts|html|css)$/.test(entry.name) && !entry.name.endsWith('.spec.ts') ? [path] : [];
  });
}

const errors = [];
for (const file of files(ROOT)) {
  const name = relative('.', file).split(sep).join('/');
  readFileSync(file, 'utf8')
    .split('\n')
    .forEach((line, index) => {
      for (const rule of RULES) {
        if (name !== rule.skip && rule.pattern.test(line)) {
          errors.push(`${name}:${index + 1} ${rule.message}`);
        }
      }
    });
}

if (errors.length > 0) {
  console.error(errors.join('\n'));
  process.exit(1);
}
console.log('Sin <img> directos ni textos recortados');
