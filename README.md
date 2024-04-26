# heimaReview
## Author: B1n_ForWhite

## Introduction
一个以用户点评和商户信息为主要内容的本地生活服务平台，基于SpringBoot实现了登陆、注册、点赞 等功能。项目注重利用Redis的特性解决不同业务场景中的问题，核心工作包括设计并实现缓存更新策略，解决缓存 击穿、缓存穿透等问题，解决优惠券超卖的线程安全问题，并基于Redis实现分布式锁。