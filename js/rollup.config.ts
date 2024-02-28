import alias from '@rollup/plugin-alias';
import commonJsPlugin from '@rollup/plugin-commonjs';
import nodeResolve from '@rollup/plugin-node-resolve';
import replace from '@rollup/plugin-replace';
import fs from 'fs';
import * as path from 'path';
import type { RollupOptions } from 'rollup';
import externals from 'rollup-plugin-node-externals';
import ts from 'rollup-plugin-ts';

function createConfig({
    bundleName,
    format,
    runtime,
}: {
    bundleName: string;
    format: 'cjs' | 'esm';
    runtime: 'browser' | 'node' | 'react-native';
}): RollupOptions {
    return {
        input: 'src/index.ts',
        output: {
            file: 'lib/' + format + '/' + bundleName,
            format,
        },
        plugins: [
            alias({
                entries: [
                    {
                        find: /^\./, // Relative paths.
                        replacement: '.',
                        async customResolver(source, importer, options) {
                            const resolved = await this.resolve(source, importer, {
                                skipSelf: true,
                                ...options,
                            });
                            if (resolved == null) {
                                return;
                            }
                            const { id: resolvedId } = resolved;
                            const directory = path.dirname(resolvedId);
                            const moduleFilename = path.basename(resolvedId);
                            const forkPath = path.join(directory, '__forks__', runtime, moduleFilename);
                            const hasForkCacheKey = `has_fork:${forkPath}`;
                            let hasFork = this.cache.get(hasForkCacheKey);
                            if (hasFork === undefined) {
                                hasFork = fs.existsSync(forkPath);
                                this.cache.set(hasForkCacheKey, hasFork);
                            }
                            if (hasFork) {
                                return forkPath;
                            }
                        },
                    },
                ],
            }),
            externals(),
            nodeResolve({
                browser: runtime === 'browser',
                extensions: ['.ts'],
                preferBuiltins: runtime === 'node',
            }),
            replace({
                preventAssignment: true,
                values: {
                    'process.env.BROWSER': JSON.stringify(runtime === 'browser'),
                    'process.env.NODE_ENV': JSON.stringify(process.env.NODE_ENV),
                },
            }),
            ts({
                tsconfig: format === 'cjs' ? 'tsconfig.cjs.json' : 'tsconfig.json',
            }),
            commonJsPlugin({ exclude: 'node_modules', extensions: ['.js', '.ts'] }),
        ],
    };
}

const configOld: RollupOptions[] = [
    // createConfig({ bundleName: 'index.js', format: 'esm', runtime: 'node' }),
    createConfig({
        bundleName: 'index.native.js',
        format: 'esm',
        runtime: 'react-native',
    }),
];

const configCjs: RollupOptions[] = [
    {
        input: 'src/index.ts',
        output: {
            file: 'lib/cjs/index.native.js',
            format: 'cjs',
        },
        plugins: [
            alias({
                entries: [
                    {
                        find: /^\./, // Relative paths.
                        replacement: '.',
                        async customResolver(source, importer, options) {
                            const resolved = await this.resolve(source, importer, {
                                skipSelf: true,
                                ...options,
                            });
                            if (resolved == null) {
                                return;
                            }
                            const { id: resolvedId } = resolved;
                            const directory = path.dirname(resolvedId);
                            const moduleFilename = path.basename(resolvedId);
                            const forkPath = path.join(directory, '__forks__', 'react-native', moduleFilename);
                            const hasForkCacheKey = `has_fork:${forkPath}`;
                            let hasFork = this.cache.get(hasForkCacheKey);
                            if (hasFork === undefined) {
                                hasFork = fs.existsSync(forkPath);
                                this.cache.set(hasForkCacheKey, hasFork);
                            }
                            if (hasFork) {
                                return forkPath;
                            }
                        },
                    },
                ],
            }),
            externals(),
            nodeResolve({
                browser: false,
                extensions: ['.ts'],
                preferBuiltins: false,
            }),
            replace({
                preventAssignment: true,
                values: {
                    'process.env.BROWSER': JSON.stringify(false),
                    'process.env.NODE_ENV': JSON.stringify(process.env.NODE_ENV),
                },
            }),
            ts({
                tsconfig: 'tsconfig.cjs.json',
            }),
            commonJsPlugin({ exclude: 'node_modules', extensions: ['.js', '.ts'] }),
        ],
    }
];

const config: RollupOptions[] = [
    {
        input: 'src/index.ts',
        output: {
            file: 'lib/esm/index.native.js',
            format: 'esm',
        },
        plugins: [
            alias({
                entries: [
                    {
                        find: /^\./, // Relative paths.
                        replacement: '.',
                        async customResolver(source, importer, options) {
                            const resolved = await this.resolve(source, importer, {
                                skipSelf: true,
                                ...options,
                            });
                            if (resolved == null) {
                                return;
                            }
                            const { id: resolvedId } = resolved;
                            const directory = path.dirname(resolvedId);
                            const moduleFilename = path.basename(resolvedId);
                            const forkPath = path.join(directory, '__forks__', "react-native", moduleFilename);
                            const hasForkCacheKey = `has_fork:${forkPath}`;
                            let hasFork = this.cache.get(hasForkCacheKey);
                            if (hasFork === undefined) {
                                hasFork = fs.existsSync(forkPath);
                                this.cache.set(hasForkCacheKey, hasFork);
                            }
                            if (hasFork) {
                                return forkPath;
                            }
                        },
                    },
                ],
            }),
            externals(),
            nodeResolve({
                browser: false,
                extensions: ['.ts'],
                preferBuiltins: false,
            }),
            replace({
                preventAssignment: true,
                values: {
                    'process.env.BROWSER': JSON.stringify(false),
                    'process.env.NODE_ENV': JSON.stringify(process.env.NODE_ENV),
                },
            }),
            ts({
                tsconfig: 'tsconfig.json',
            }),
            commonJsPlugin({ exclude: 'node_modules', extensions: ['.js', '.ts'] }),
        ],
    }
];

export default config;