import React from 'react';
import { createRoot } from 'react-dom/client';
import { BrowserRouter } from 'react-router-dom';

import 'bootstrap/dist/css/bootstrap.min.css';
import 'bootstrap-icons/font/bootstrap-icons.css';
// After Bootstrap: the brand palette and component overrides win.
import './styles/styles.css';
// Registers Bootstrap's data-api handlers for dropdown / collapse / offcanvas.
// They delegate from document, so they work on React-rendered markup.
import 'bootstrap';

import App from './App.jsx';
import { SessionProvider } from './context/SessionContext.jsx';
import { AlertProvider } from './context/AlertContext.jsx';

createRoot(document.getElementById('root')).render(
    <React.StrictMode>
        <BrowserRouter>
            <AlertProvider>
                <SessionProvider>
                    <App />
                </SessionProvider>
            </AlertProvider>
        </BrowserRouter>
    </React.StrictMode>
);
