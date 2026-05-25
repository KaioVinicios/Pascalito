program completo;
var
  x, y, z: integer;
  ok: boolean;
  nome: string;
begin
  x := 10;
  y := -5;
  z := (x + y) * 2 - 1 / 3;
  ok := (x > 0) and (y < 0) or ~false;
  read(nome);
  if x >= y then
    write("x maior ou igual", x, y, nome)
  else
    write("x menor", nome);
  while x <> 0 do
  begin
    x := x - 1;
    if ok then
      write(x)
    else
      read(x)
  end
end.
