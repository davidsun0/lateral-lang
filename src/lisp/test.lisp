(defun asdf (a :rest b)
  (list a b))

(asdf 1 2 3 4 5)

(defmacro prep (a b)
  (let (LinkedList     "io/github/whetfire/lateral/LinkedList"
        LinkedListType "Lio/github/whetfire/lateral/LinkedList;"
        ObjectType     "Ljava/lang/Object;")
    (list (quote asm)
          (list :new LinkedList)
          :dup
          a
          b
          (list :checkcast LinkedList)
          (list :invokespecial LinkedList "<init>"
                "(Ljava/lang/Object;Lio/github/whetfire/lateral/LinkedList;)V"))))

(defun cons (a b)
  (prep a b))

cons

(cons 1 null)

asdfsdfajk
