-- KEY[1]: 'ticket_stock:{ticketTypeId}'
-- ARGV[1]: quantity cần giảm
-- ARGV[2]: quantity trong DB (dùng khi lazy init)

local stock = redis.call('GET', KEYS[1])

-- Lazy init: nếu key chưa có, đọc từ DB (ARGV[2])
if stock == false then
	redis.call('SET', KEYS[1], ARGV[2], 'NX')
	stock = ARGV[2]
end

local qty = tonumber(ARGV[1])
local current = tonumber(stock)

if current < qty then
	return -1  -- sold out
end

return redis.call('DECRBY', KEYS[1], qty)  -- trả về remaining
