program tipos;
var
  n: integer;
  b: boolean;
begin
  n := true;
  b := n + 1;
  n := b and false;
  if n then
    n := 1
  else
    n := 2;
  while n + 1 do
    n := ~n
end.
