module.exports = {
    devServer: {
        proxy: {
            '^/api': {
                // Forward calls to the API to a running Mantik Engine
                target: 'http://localhost:4040',
                ws: true,
                changeOrigin: true
            },
        }
    }
};