# 第三方许可与署名

PocketDesk 复用了下列 MIT 许可项目的**数据与实现思路**，代码已按原许可保留署名。
以下列出使用时参考的上游提交；公开发布前应继续核查全部依赖、素材及其他引用。

| 项目 | 许可证 | commit | 本项目的使用方式 |
| --- | --- | --- | --- |
| [Linkpad](https://github.com/Devdas-gupta/linkpad) © 2026 Devdas Kumar | MIT | `e08cc8daa8db87dba74f8e823f7dd84443ca6788` | HID 描述符方案 A（键盘 Report ID 1 / 鼠标 2 / 消费者 3，5 键鼠标 + 横滚）逐字节取用；前台服务连接生命周期与保活策略参考其公开实现 |
| [Beckon](https://github.com/omsingh02/beckon) © 2026 Om Singh | MIT | `b6814bcc7f8a6813bd5e821a05ea1b02c3e1a082` | HID 描述符方案 B（鼠标 Report ID 1，16 位相对位移 / 键盘 2 / 消费者 3）逐字节取用；注册、失败分类与线程模型参考其公开实现 |
| [Sofatap](https://github.com/chekusu/sofatap) © 2026 chekusu and contributors | MIT | `754d22b6f671b034e876d12f7c4ee980749a13d8` | 仅作协议与安全设计参考（配对 QR、证书固定、TV 适配边界），**未复用代码** |

MIT 许可允许在保留版权与许可声明的前提下修改与再利用。本项目未复制任何 GPL / AGPL 代码
（任务书列出的 Axon 为 GPL-3.0，未使用）。

以下许可文本分别取自上述固定提交的 [Linkpad LICENSE](https://github.com/Devdas-gupta/linkpad/blob/e08cc8daa8db87dba74f8e823f7dd84443ca6788/LICENSE) 和 [Beckon LICENSE](https://github.com/omsingh02/beckon/blob/b6814bcc7f8a6813bd5e821a05ea1b02c3e1a082/LICENSE)。本文件中的第三方许可不决定 PocketDesk 自身的许可证。

## Linkpad

```text
MIT License

Copyright (c) 2026 Devdas Kumar

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE.
```

## Beckon

```text
MIT License

Copyright (c) 2026 Om Singh

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in
all copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE.
```
