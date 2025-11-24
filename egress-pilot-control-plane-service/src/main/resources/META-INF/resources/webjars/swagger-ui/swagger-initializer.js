window.onload = async function () {
  const fallbackKey = 'changeme-control-plane-key';

  const loadApiKey = async () => {
    try {
      const response = await fetch('/swagger-ui/api-key.json');
      if (response.ok) {
        const body = await response.json();
        if (body && body.apiKey) {
          return body.apiKey;
        }
      }
    } catch (err) {
      // ignore and fall back
    }
    return fallbackKey;
  };

  const defaultApiKey = await loadApiKey();

  const ui = SwaggerUIBundle({
    configUrl: '/v3/api-docs/swagger-config',
    dom_id: '#swagger-ui',
    deepLinking: true,
    persistAuthorization: true,
    requestInterceptor: (req) => {
      if (!req.headers['X-API-KEY']) {
        req.headers['X-API-KEY'] = defaultApiKey;
      }
      return req;
    },
    presets: [
      SwaggerUIBundle.presets.apis,
      SwaggerUIStandalonePreset
    ],
    plugins: [
      SwaggerUIBundle.plugins.DownloadUrl
    ],
    layout: 'StandaloneLayout'
  });

  // Pre-authorize the API key security scheme so "Try it out" works immediately.
  ui.preauthorizeApiKey('apiKey', defaultApiKey);

  window.ui = ui;
};
