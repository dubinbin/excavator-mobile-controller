import { execFileSync } from 'node:child_process'
import { readFileSync, writeFileSync } from 'node:fs'
import path from 'node:path'
import { fileURLToPath } from 'node:url'

const projectRoot = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..')
const packageJsonPath = path.join(projectRoot, 'package.json')
const packageLockPath = path.join(projectRoot, 'package-lock.json')
const binExtension = process.platform === 'win32' ? '.cmd' : ''

const readJson = (filePath) => JSON.parse(readFileSync(filePath, 'utf8'))
const writeJson = (filePath, value) => {
  writeFileSync(filePath, `${JSON.stringify(value, null, 2)}\n`)
}

const incrementVersion = (version) => {
  const match = /^(\d+)\.(\d+)\.(\d+)$/.exec(version)

  if (!match) {
    throw new Error(`Unsupported version format: ${version}`)
  }

  let [, major, minor, patch] = match.map(Number)
  patch += 1

  if (patch >= 100) {
    patch = 0
    minor += 1
  }

  if (minor >= 100) {
    minor = 0
    major += 1
  }

  return `${major}.${minor}.${patch}`
}

const packageJson = readJson(packageJsonPath)
const nextVersion = incrementVersion(packageJson.version)
const buildEnvironment = { ...process.env, BUILD_VERSION: nextVersion }

const run = (command, args) => {
  execFileSync(path.join(projectRoot, 'node_modules', '.bin', `${command}${binExtension}`), args, {
    cwd: projectRoot,
    env: buildEnvironment,
    stdio: 'inherit',
  })
}

run('tsc', ['-b'])
run('vite', ['build'])

packageJson.version = nextVersion
writeJson(packageJsonPath, packageJson)

const packageLock = readJson(packageLockPath)
packageLock.version = nextVersion
packageLock.packages[''].version = nextVersion
writeJson(packageLockPath, packageLock)

console.log(`Build completed. Version updated to ${nextVersion}.`)
