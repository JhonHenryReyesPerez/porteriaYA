// Comprueba que el jsCode de cada nodo Code es JS valido y que las conexiones
// de cada flujo apuntan a nodos que existen. No ejecuta nada: solo compila.
const fs = require('fs');
const path = require('path');

const dir = process.argv[2];
let fallos = 0;

for (const archivo of fs.readdirSync(dir).filter((f) => f.endsWith('.json')).sort()) {
  const j = JSON.parse(fs.readFileSync(path.join(dir, archivo), 'utf8'));
  const nombres = new Set(j.nodes.map((n) => n.name));
  const problemas = [];

  for (const nodo of j.nodes) {
    if (nodo.type === 'n8n-nodes-base.code') {
      try {
        // Sin ejecutar: solo se compila el cuerpo de la funcion.
        new Function('$input', '$json', '$env', '$', 'console', nodo.parameters.jsCode);
      } catch (e) {
        problemas.push('jsCode invalido en "' + nodo.name + '": ' + e.message);
      }
    }
  }

  for (const [origen, salidas] of Object.entries(j.connections || {})) {
    if (!nombres.has(origen)) {
      problemas.push('conexion desde un nodo que no existe: ' + origen);
    }
    for (const rama of salidas.main || []) {
      for (const destino of rama || []) {
        if (!nombres.has(destino.node)) {
          problemas.push('conexion hacia un nodo que no existe: ' + destino.node);
        }
      }
    }
  }

  const conEntrada = new Set();
  for (const salidas of Object.values(j.connections || {})) {
    for (const rama of salidas.main || []) {
      for (const d of rama || []) conEntrada.add(d.node);
    }
  }
  const huerfanos = j.nodes
    .filter((n) => !conEntrada.has(n.name) && !Object.keys(j.connections || {}).includes(n.name))
    .map((n) => n.name);
  if (huerfanos.length) problemas.push('nodos sueltos: ' + huerfanos.join(', '));

  const creds = j.nodes.flatMap((n) => Object.values(n.credentials || {}));
  const filtradas = creds.filter((c) => c.id !== 'REEMPLAZAR');
  if (filtradas.length) {
    problemas.push('credencial con id real: ' + JSON.stringify(filtradas));
  }

  if (problemas.length) {
    fallos += problemas.length;
    console.log('FALLO  ' + archivo);
    problemas.forEach((p) => console.log('       - ' + p));
  } else {
    console.log('OK     ' + archivo + '  (' + j.nodes.length + ' nodos)');
  }
}

console.log(fallos === 0 ? '\nTodos los flujos pasan.' : '\n' + fallos + ' problemas.');
process.exit(fallos === 0 ? 0 : 1);
