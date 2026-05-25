program exemplo;
var
  x, y: integer;
  flag: boolean;
  msg: string;
begin
  x := 10;
  y := -5;
  flag := true and (x > y) or ~false;
  msg := "ola mundo";
  if x >= y then
    write("maior ou igual", x, y)
  else
    write("menor");
  while x <> 0 do
  begin
    x := x - 1;
    read(x)
  end
end.
