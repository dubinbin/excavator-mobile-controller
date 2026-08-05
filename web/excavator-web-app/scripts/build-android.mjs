import { execFileSync } from 'node:child_process'
import { createHash } from 'node:crypto'
import { readdirSync, readFileSync, statSync } from 'node:fs'
import path from 'node:path'
import { fileURLToPath } from 'node:url'

const projectRoot = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..')
const binExtension = process.platform === 'win32' ? '.cmd' : ''
const packageJson = JSON.parse(
  readFileSync(path.join(projectRoot, 'package.json'), 'utf8'),
)

const hash = createHash('sha256')
const buildInputs = [
  'index.html',
  'package-lock.json',
  'package.json',
  'src',
  'tsconfig.app.json',
  'tsconfig.json',
  'tsconfig.node.json',
  'vite.config.ts',
]

const addToHash = (relativePath) => {
  const absolutePath = path.join(projectRoot, relativePath)
  const stat = statSync(absolutePath)

  if (stat.isDirectory()) {
    for (const child of readdirSync(absolutePath).sort()) {
      addToHash(path.join(relativePath, child))
    }
    return
  }

  hash.update(relativePath.split(path.sep).join('/'))
  hash.update('\0')
  hash.update(readFileSync(absolutePath))
  hash.update('\0')
}

for (const input of buildInputs) {
  addToHash(input)
}

const sourceHash = hash.digest('hex').slice(0, 12)
const buildVersion = `${packageJson.version}+${sourceHash}`
const buildEnvironment = { ...process.env, BUILD_VERSION: buildVersion }

const run = (command, args) => {
  execFileSync(
    path.join(projectRoot, 'node_modules', '.bin', `${command}${binExtension}`),
    args,
    {
      cwd: projectRoot,
      env: buildEnvironment,
      stdio: 'inherit',
    },
  )
}

run('tsc', ['-b'])
run('vite', ['build'])

console.log(`Android web assets built with version ${buildVersion}.`)
