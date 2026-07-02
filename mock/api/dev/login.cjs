// GET /api/dev/login?userId=xxx  → 开发期签发 JWT
// 注:Connect 中间件挂载在 /api, req.url 已剥前缀 → segments = ['dev', 'login']
module.exports = () => {
  return {
    data: {
      token: 'mock-jwt-token-for-development',
      userId: '00000000-0000-0000-0000-0000000000aa',
    },
  }
}
