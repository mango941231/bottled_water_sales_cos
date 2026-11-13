package cc.mrbird.febs.cos.service;

import cc.mrbird.febs.cos.dao.WaterTicketAccountMapper;
import cc.mrbird.febs.cos.dao.WaterTicketPackageMapper;
import cc.mrbird.febs.cos.dao.WaterTicketUsageMapper;
import cc.mrbird.febs.cos.entity.*;
import cc.mrbird.febs.cos.service.ICommodityInfoService;
import cc.mrbird.febs.cos.service.IShopInfoService;
import cc.mrbird.febs.cos.service.IUserInfoService;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.*;

@Service
@RequiredArgsConstructor
public class WaterTicketService {
    private final WaterTicketPackageMapper packageMapper;
    private final WaterTicketAccountMapper accountMapper;
    private final WaterTicketUsageMapper usageMapper;
    private final ICommodityInfoService commodityService;
    private final IShopInfoService shopService;
    private final IUserInfoService userService;

    public List<WaterTicketPackage> listForCommodity(Integer commodityId) {
        return packageMapper.selectList(Wrappers.<WaterTicketPackage>lambdaQuery()
                .eq(WaterTicketPackage::getCommodityId, commodityId)
                .eq(WaterTicketPackage::getStatus, 1)
                .orderByAsc(WaterTicketPackage::getPrice));
    }

    public List<WaterTicketPackage> listForShopUser(Integer systemUserId) {
        ShopInfo shop = getShopForSystemUser(systemUserId);
        return packageMapper.selectList(Wrappers.<WaterTicketPackage>lambdaQuery()
                .eq(WaterTicketPackage::getShopId, shop.getId())
                .orderByDesc(WaterTicketPackage::getCreateTime));
    }

    public WaterTicketPackage savePackage(Integer systemUserId, WaterTicketPackage ticketPackage) {
        ShopInfo shop = getShopForSystemUser(systemUserId);
        CommodityInfo commodity = commodityService.getById(ticketPackage.getCommodityId());
        if (commodity == null || !shop.getId().equals(commodity.getShopId())) {
            throw new IllegalArgumentException("只能为本店商品创建水票套餐");
        }
        if (ticketPackage.getTicketNum() == null || ticketPackage.getTicketNum() <= 0 ||
                ticketPackage.getValidDays() == null || ticketPackage.getValidDays() <= 0 ||
                ticketPackage.getPrice() == null || ticketPackage.getPrice().compareTo(BigDecimal.ZERO) < 0 ||
                ticketPackage.getName() == null || ticketPackage.getName().trim().isEmpty()) {
            throw new IllegalArgumentException("请填写有效的套餐名称、桶数、售价和有效天数");
        }
        ticketPackage.setShopId(shop.getId());
        ticketPackage.setName(ticketPackage.getName().trim());
        if (ticketPackage.getStatus() == null) ticketPackage.setStatus(1);
        if (ticketPackage.getId() == null) {
            packageMapper.insert(ticketPackage);
        } else {
            WaterTicketPackage old = packageMapper.selectById(ticketPackage.getId());
            if (old == null || !shop.getId().equals(old.getShopId())) {
                throw new IllegalArgumentException("水票套餐不存在或无权修改");
            }
            packageMapper.updateById(ticketPackage);
        }
        return ticketPackage;
    }

    @Transactional(rollbackFor = Exception.class)
    public WaterTicketAccount purchase(Integer userId, Integer packageId) {
        if (userService.getById(userId) == null) throw new IllegalArgumentException("用户不存在");
        WaterTicketPackage ticketPackage = packageMapper.selectById(packageId);
        if (ticketPackage == null || ticketPackage.getStatus() != 1) throw new IllegalArgumentException("水票套餐已下架");
        CommodityInfo commodity = commodityService.getById(ticketPackage.getCommodityId());
        if (commodity == null || !"1".equals(commodity.getOnPut())) throw new IllegalArgumentException("关联商品已下架");

        Date now = new Date();
        WaterTicketAccount account = new WaterTicketAccount();
        account.setUserId(userId);
        account.setShopId(ticketPackage.getShopId());
        account.setCommodityId(ticketPackage.getCommodityId());
        account.setPackageId(ticketPackage.getId());
        account.setPackageName(ticketPackage.getName());
        account.setPurchasePrice(ticketPackage.getPrice());
        account.setTotalNum(ticketPackage.getTicketNum());
        account.setRemainingNum(ticketPackage.getTicketNum());
        Calendar calendar = Calendar.getInstance();
        calendar.setTime(now);
        calendar.add(Calendar.DAY_OF_YEAR, ticketPackage.getValidDays());
        account.setExpireTime(calendar.getTime());
        account.setCreateTime(now);
        account.setStatus(1);
        accountMapper.insert(account);
        return account;
    }

    public List<Map<String, Object>> listAccounts(Integer userId) {
        List<WaterTicketAccount> accounts = accountMapper.selectList(Wrappers.<WaterTicketAccount>lambdaQuery()
                .eq(WaterTicketAccount::getUserId, userId)
                .orderByAsc(WaterTicketAccount::getExpireTime)
                .orderByDesc(WaterTicketAccount::getCreateTime));
        List<Map<String, Object>> result = new ArrayList<>();
        for (WaterTicketAccount account : accounts) {
            CommodityInfo commodity = commodityService.getById(account.getCommodityId());
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", account.getId());
            row.put("packageName", account.getPackageName());
            row.put("purchasePrice", account.getPurchasePrice());
            row.put("commodityId", account.getCommodityId());
            row.put("commodityName", commodity == null ? "商品已删除" : commodity.getName());
            row.put("totalNum", account.getTotalNum());
            row.put("remainingNum", account.getRemainingNum());
            row.put("expireTime", account.getExpireTime());
            row.put("createTime", account.getCreateTime());
            row.put("expired", account.getExpireTime().before(new Date()));
            result.add(row);
        }
        return result;
    }

    public int available(Integer userId, Integer commodityId) {
        Date now = new Date();
        List<WaterTicketAccount> accounts = accountMapper.selectList(Wrappers.<WaterTicketAccount>lambdaQuery()
                .eq(WaterTicketAccount::getUserId, userId)
                .eq(WaterTicketAccount::getCommodityId, commodityId)
                .eq(WaterTicketAccount::getStatus, 1)
                .gt(WaterTicketAccount::getExpireTime, now)
                .gt(WaterTicketAccount::getRemainingNum, 0));
        return accounts.stream().mapToInt(WaterTicketAccount::getRemainingNum).sum();
    }

    @Transactional(rollbackFor = Exception.class)
    public int consume(Integer userId, Integer shopId, Integer commodityId, int requested, String orderCode) {
        if (requested <= 0) return 0;
        List<WaterTicketAccount> accounts = accountMapper.selectList(Wrappers.<WaterTicketAccount>lambdaQuery()
                .eq(WaterTicketAccount::getUserId, userId)
                .eq(WaterTicketAccount::getShopId, shopId)
                .eq(WaterTicketAccount::getCommodityId, commodityId)
                .eq(WaterTicketAccount::getStatus, 1)
                .gt(WaterTicketAccount::getExpireTime, new Date())
                .gt(WaterTicketAccount::getRemainingNum, 0)
                .orderByAsc(WaterTicketAccount::getExpireTime)
                .orderByAsc(WaterTicketAccount::getId));
        int left = requested;
        for (WaterTicketAccount account : accounts) {
            int use = Math.min(left, account.getRemainingNum());
            if (use > 0) {
                if (accountMapper.consume(account.getId(), use) != 1) {
                    throw new IllegalArgumentException("水票余额已变化，请刷新后重试");
                }
                WaterTicketUsage usage = new WaterTicketUsage();
                usage.setAccountId(account.getId());
                usage.setOrderCode(orderCode);
                usage.setUserId(userId);
                usage.setShopId(shopId);
                usage.setCommodityId(commodityId);
                usage.setTicketNum(use);
                usage.setCreateTime(new Date());
                usageMapper.insert(usage);
                left -= use;
            }
            if (left == 0) break;
        }
        if (left > 0) throw new IllegalArgumentException("水票余额不足");
        return requested;
    }

    public ShopInfo getShopForSystemUser(Integer systemUserId) {
        ShopInfo shop = shopService.getOne(Wrappers.<ShopInfo>lambdaQuery().eq(ShopInfo::getSysUserId, systemUserId));
        if (shop == null) throw new IllegalArgumentException("当前账号没有关联商铺");
        return shop;
    }
}
