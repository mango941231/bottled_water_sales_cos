package cc.mrbird.febs.cos.service;

import cc.mrbird.febs.cos.dao.WaterTicketAccountMapper;
import cc.mrbird.febs.cos.dao.WaterTicketPackageMapper;
import cc.mrbird.febs.cos.dao.WaterTicketUsageMapper;
import cc.mrbird.febs.cos.entity.*;
import cc.mrbird.febs.cos.service.ICommodityInfoService;
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
    private final IUserInfoService userService;

    public List<WaterTicketPackage> listForCommodity(Integer commodityId) {
        return packageMapper.selectList(Wrappers.<WaterTicketPackage>lambdaQuery()
                .eq(WaterTicketPackage::getCommodityId, commodityId)
                .eq(WaterTicketPackage::getStatus, 1)
                .orderByAsc(WaterTicketPackage::getPrice));
    }

    public List<WaterTicketPackage> listAllPackages() {
        return packageMapper.selectList(Wrappers.<WaterTicketPackage>lambdaQuery()
                .orderByDesc(WaterTicketPackage::getCreateTime));
    }

    public List<Map<String, Object>> listAvailablePackages() {
        List<WaterTicketPackage> packages = packageMapper.selectList(Wrappers.<WaterTicketPackage>lambdaQuery()
                .eq(WaterTicketPackage::getStatus, 1).orderByDesc(WaterTicketPackage::getCreateTime));
        List<Map<String, Object>> result = new ArrayList<>();
        for (WaterTicketPackage ticketPackage : packages) {
            CommodityInfo commodity = commodityService.getById(ticketPackage.getCommodityId());
            if (commodity == null || !"1".equals(commodity.getOnPut())) continue;
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", ticketPackage.getId());
            item.put("commodityId", ticketPackage.getCommodityId());
            item.put("commodityName", commodity.getName());
            item.put("commodityImage", commodity.getImages());
            item.put("name", ticketPackage.getName());
            item.put("ticketNum", ticketPackage.getTicketNum());
            item.put("validDays", ticketPackage.getValidDays());
            item.put("originalPrice", ticketPackage.getOriginalPrice() == null ? ticketPackage.getPrice() : ticketPackage.getOriginalPrice());
            item.put("discountPrice", ticketPackage.getDiscountPrice() == null ? ticketPackage.getPrice() : ticketPackage.getDiscountPrice());
            result.add(item);
        }
        return result;
    }

    public WaterTicketPackage savePackageAsAdmin(WaterTicketPackage ticketPackage) {
        CommodityInfo commodity = commodityService.getById(ticketPackage.getCommodityId());
        if (commodity == null) {
            throw new IllegalArgumentException("关联商品不存在");
        }
        if (ticketPackage.getDiscountPrice() != null) ticketPackage.setPrice(ticketPackage.getDiscountPrice());
        if (ticketPackage.getOriginalPrice() == null) ticketPackage.setOriginalPrice(ticketPackage.getPrice());
        if (ticketPackage.getDiscountPrice() == null) ticketPackage.setDiscountPrice(ticketPackage.getPrice());
        validatePackage(ticketPackage);
        if (ticketPackage.getDiscountPrice().compareTo(ticketPackage.getOriginalPrice()) > 0) {
            throw new IllegalArgumentException("优惠价不能高于原价");
        }
        ticketPackage.setName(ticketPackage.getName().trim());
        if (ticketPackage.getStatus() == null) ticketPackage.setStatus(1);
        if (ticketPackage.getId() == null) {
            packageMapper.insert(ticketPackage);
        } else {
            WaterTicketPackage old = packageMapper.selectById(ticketPackage.getId());
            if (old == null) throw new IllegalArgumentException("水票套餐不存在");
            packageMapper.updateById(ticketPackage);
        }
        return ticketPackage;
    }

    public WaterTicketPackage disablePackageAsAdmin(Integer id) {
        WaterTicketPackage ticketPackage = packageMapper.selectById(id);
        if (ticketPackage == null) throw new IllegalArgumentException("水票套餐不存在");
        ticketPackage.setStatus(0);
        packageMapper.updateById(ticketPackage);
        return ticketPackage;
    }

    private void validatePackage(WaterTicketPackage ticketPackage) {
        if (ticketPackage.getTicketNum() == null || ticketPackage.getTicketNum() <= 0 ||
                ticketPackage.getValidDays() == null || ticketPackage.getValidDays() <= 0 ||
                ticketPackage.getPrice() == null || ticketPackage.getPrice().compareTo(BigDecimal.ZERO) < 0 ||
                ticketPackage.getName() == null || ticketPackage.getName().trim().isEmpty()) {
            throw new IllegalArgumentException("请填写有效的套餐名称、桶数、售价和有效天数");
        }
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
            row.put("status", account.getStatus());
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
    public int consume(Integer userId, Integer commodityId, int requested, String orderCode) {
        if (requested <= 0) return 0;
        List<WaterTicketAccount> accounts = accountMapper.selectList(Wrappers.<WaterTicketAccount>lambdaQuery()
                .eq(WaterTicketAccount::getUserId, userId)
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

}
