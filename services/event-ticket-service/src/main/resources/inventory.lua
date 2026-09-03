-- =========================================================================
-- KEYS[1]: 'ticket_stock:{ticketTypeId}'
-- ARGV[1]: Số lượng vé cần mua/giảm (quantity to decrement)
-- ARGV[2]: Số lượng vé ban đầu từ DB (dùng khi Redis Cache Miss / Cold Cache)
-- =========================================================================

local stock = redis.call('GET', KEYS[1])

-- 1. LAZY INITIALIZATION: Nếu Redis chưa có key (Cold Cache), nạp từ DB (ARGV[2])
if stock == false then
  redis.call('SET', KEYS[1], ARGV[2], 'NX')
  stock = ARGV[2]
end

local qty = tonumber(ARGV[1])
local current = tonumber(stock)

-- 2. KIỂM TRA TỒN KHO
if current < qty then
  return -1  -- Trả về -1 biểu thị Hết vé (Sold Out)
end

-- 3. TRỪ KHO NGUYÊN TỬ (ATOMIC DECREMENT)
return redis.call('DECRBY', KEYS[1], qty)  -- Trả về số lượng vé còn lại
