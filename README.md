# xhs-phone

第三方小红书手机客户端（重写版），Kotlin + Jetpack Compose。

## 功能（MVP）
- [x] 登录：WebView 加载官方登录页（支持手机号+验证码），Cookie 持久化
- [x] 首页推荐信息流（瀑布流）
- [ ] 笔记详情 + 评论
- [ ] 搜索
- [ ] 个人中心

## 技术
- 网络：OkHttp，直接调用小红书网页版 API（`edith.xiaohongshu.com/api/sns/web/...`），Cookie 鉴权
- 图片：Coil，视频：Media3
- 接口路径来自对 xhsTV 0.2.10 的逆向分析

## 构建
CI（GitHub Actions）签名构建，Release 自动发布 APK。

## 声明
仅供学习研究使用，请遵守小红书服务条款与相关法律法规。
