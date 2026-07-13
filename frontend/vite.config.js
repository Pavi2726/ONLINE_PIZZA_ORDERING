import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';

export default defineConfig({
    plugins: [react()],
    server: {
        port: 5173,
        proxy: {
            '/api': {
                target: 'http://localhost:8080',
                changeOrigin: false,
            },
        },
    },
    build: {
        // Emitted straight into Spring Boot's classpath static resources so
        // `mvn spring-boot:run` and the packaged jar both serve the SPA.
        outDir: '../target/classes/static',
        // maven-resources-plugin also writes into target/classes; never wipe it.
        emptyOutDir: false,
    },
    test: {
        environment: 'jsdom',
        globals: true,
        setupFiles: './src/test/setup.js',
    },
});
