SELECT u.name, SUM(o.total_price), COUNT(o.id)
FROM users u
JOIN orders o ON o.user_id = u.id
GROUP BY u.id
HAVING SUM(o.total_price) >= 100000
